package com.jentfoo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.threadly.concurrent.PriorityScheduledExecutor;
import org.threadly.concurrent.PrioritySchedulerInterface;
import org.threadly.concurrent.TaskPriority;

public class MediaConverter {
  private static final boolean VERBOSE = true;
  private static final short THREAD_COUNT = 8;
  private static final short ENCODE_PARALLEL_COUNT = 4;
  private static final long MAX_RUN_TIME = 1000 * 60 * 60 * 24 * 2; // 2 days in millis
  private static final ConverterType DEFAULT_CONVERTER_TYPE = ConverterType.Mencoder; // TODO - make type automatically picked
  
  public enum ConverterType { 
    Mencoder;

    public static ConverterType parse(String type) {
      if (Mencoder.name().equalsIgnoreCase(type)) {
        return Mencoder;
      } else {
        throw new IllegalArgumentException("Unknown converter type: " + type);
      }
    }
  };
  
  public static void main(String args[]) {
    if (args.length >= 2) {
      // TODO - print better usage statement
      throw new IllegalArgumentException("Must supply two arguments, source folder, destination folder");
    }
    
    File sourceFolder = new File(args[0]);
    File destFolder = new File(args[1]);
    if (! sourceFolder.exists()) {
      throw new IllegalStateException("Source folder does not exist");
    } else if (! sourceFolder.isDirectory()) {
      throw new IllegalStateException("Source folder is not a folder");
    } else if (! destFolder.exists()) {
      if (! destFolder.mkdirs()) {
        throw new IllegalStateException("Could not make destination folder");
      }
    } else if (! destFolder.isDirectory()) {
      throw new IllegalStateException("Destination folder is not a folder");
    }
    
    ConverterType converterType = DEFAULT_CONVERTER_TYPE;
    if (args.length > 2) {
      converterType = ConverterType.parse(args[2]);
    }
    
    ConverterInterface converter;
    switch (converterType) {
      case Mencoder:
        converter = new MencoderConverter();
        break;
      // TODO - add libav
      default:
        throw new UnsupportedOperationException("Unhandled converter type: " + converterType);
    }
    
    startProcessingFiles(converter, destFolder, sourceFolder);
  }
  
  private static void startProcessingFiles(ConverterInterface converter, 
                                           File destFolder, File sourceFolder) {
    PriorityScheduledExecutor scheduler = new PriorityScheduledExecutor(ENCODE_PARALLEL_COUNT, THREAD_COUNT, 10 * 1000, 
                                                                        TaskPriority.High, 10 * 1000, true);
    
    try {
      File[] origDestFileArray = destFolder.listFiles();
      File[] sourceFileArray = sourceFolder.listFiles();
      
      List<File> sourceFileList = makeValidSourceList(converter, 
                                                      sourceFileArray, 
                                                      destFolder, 
                                                      origDestFileArray);
      
      PrioritySchedulerInterface converterPool = scheduler.makeSubPool(ENCODE_PARALLEL_COUNT);
      Map<File, Future<?>> jobs = converter.submitJobs(converterPool, 
                                                       sourceFileList, 
                                                       destFolder);
      
      scheduleKillTask(scheduler, converter, jobs, destFolder);
  
      // wait for all running processes to finish
      waitForJobs(jobs);
      
      // TODO - schedule this on the converterPool before waitForJobs
      deleteRemovedFiles(converter, origDestFileArray, 
                         sourceFolder.listFiles(), // get new list, in case some were deleted while processing
                         destFolder);
    } finally {
      scheduler.shutdown();
    }
  }
  
  private static void scheduleKillTask(PrioritySchedulerInterface scheduler, 
                                       ConverterInterface converter, 
                                       Map<File, Future<?>> jobs, 
                                       File destFolder) {
    scheduler.schedule(new TimeoutKiller(converter, jobs, destFolder), 
                       MAX_RUN_TIME, TaskPriority.Low);
  }
  
  private static List<File> makeValidSourceList(ConverterInterface converter, File[] sourceFileArray, 
                                                File destFolder, File[] origDestFileArray) {
    List<File> sourceFileList = new ArrayList<File>(sourceFileArray.length);
    for (int i = 0; i < sourceFileArray.length; i++) {
      File sourceFile = sourceFileArray[i]; 
      if (sourceFile.isFile()) {
        if (sourceFile.canRead()) {
          sourceFileList.add(sourceFile);
        } else {
          System.err.print("Can not read file: " + sourceFile.getAbsolutePath());
          
          File newFile = FileUtils.makeNewFile(destFolder, sourceFile, converter.getProducedExtesion());
          if (FileUtils.fileInArray(origDestFileArray, newFile)) {
            System.err.println("...will not remove already converted file: " + newFile.getAbsolutePath());
            
            // add so the already converted file is not removed
            sourceFileList.add(sourceFile);
          } else {
            System.err.println();
          }
        }
      }
    }
    
    return sourceFileList;
  }
  
  private static void waitForJobs(Map<File, Future<?>> jobs) {
    jobs = new HashMap<File, Future<?>>(jobs);  // make copy so we can modify
    
    Iterator<Entry<File, Future<?>>> futureIt = jobs.entrySet().iterator();
    while (futureIt.hasNext()) {
      Entry<File, Future<?>> futureEntry = futureIt.next();
      try {
        try {
          futureEntry.getValue().get(1000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
          // if it timed out log and wait for item to finish...
          if (VERBOSE) {
            System.out.println("Waiting on " + jobs.size() + " conversions to finish");
          }
          
          // wait for it to finish
          futureEntry.getValue().get();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        System.out.println("Exception processing file: " + futureEntry.getKey());
        e.printStackTrace(System.err);
      } finally {
        futureIt.remove();
      }
    }
  }
  
  // TODO - run this in parallel with conversions
  private static void deleteRemovedFiles(ConverterInterface converter, 
                                         File[] origDestFileList, 
                                         File[] sourceFileArray, 
                                         File destFolder) {
    for (int i = 0; i < origDestFileList.length; i++) {
      boolean stillExists = false;
      for (int j = 0; j < sourceFileArray.length; j++) {
        String destFilePath = FileUtils.makeNewFile(destFolder, sourceFileArray[j], 
                                                    converter.getProducedExtesion()).getAbsolutePath();
        if (destFilePath.equals(origDestFileList[i].getAbsolutePath())) {
          stillExists = true;
          break;
        }
      }
      
      if (! stillExists) {
        if (VERBOSE) {
          System.out.println("Deleting file: " + origDestFileList[i].getAbsolutePath());
        }
        if (! origDestFileList[i].delete()) {
          System.err.println("Failed to delete file: " + origDestFileList[i].getAbsolutePath());
        }
      }
    }
  }
  
  private static class TimeoutKiller implements Runnable {
    private final ConverterInterface converter;
    private final Map<File, Future<?>> jobs;
    private final File destFolder;
    
    private TimeoutKiller(ConverterInterface converter, 
                          Map<File, Future<?>> jobs, 
                          File destFolder) {
      this.converter = converter;
      this.jobs = jobs;
      this.destFolder = destFolder;
    }
    
    @Override
    public void run() {
      Iterator<Entry<File, Future<?>>> it = jobs.entrySet().iterator();
      while (it.hasNext()) {
        Entry<File, Future<?>> entry = it.next();
        try {
          entry.getValue().get(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
          // ignored
        } catch (TimeoutException e) {
          // still running, so remove failed file
          File newFile = FileUtils.makeNewFile(destFolder, entry.getKey(), converter.getProducedExtesion());
          if (! newFile.delete()) {
            System.err.println("Could not delete in progress file: " + newFile.getAbsolutePath());
          } else {
            System.err.println("Deleted in progress file: " + newFile.getAbsolutePath());
          }
        }
      }
      
      System.exit(1);
    }
  }
}
