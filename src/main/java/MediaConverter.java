import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.threadly.concurrent.PriorityScheduledExecutor;
import org.threadly.concurrent.SubmitterSchedulerInterface;
import org.threadly.concurrent.TaskPriority;
import org.threadly.util.Clock;
import org.threadly.util.ExceptionUtils;

public class MediaConverter {
  private static final boolean VERBOSE = true;
  private static final Short THREAD_COUNT = 4;
  //private static final String FLAGS = "-oac mp3lame -lameopts vol=5.5 " +
  private static final String FLAGS = "-oac mp3lame -ovc xvid -xvidencopts fixed_quant=2 -sws 8";
  //private static final String FLAGS = "-oac mp3lame -ovc lavc -lavcopts vcodec=mpeg4:vhq:vbitrate=8000";
  private static final int FILE_SIZE_STABILITY_TIME_IN_MILLIS = 1000 * 10;
  private static final String DESIRED_EXTENSION = ".avi";
  private static final int BUFFER_SIZE = 4096;
  private static final long MAX_RUN_TIME = 1000 * 60 * 60 * 24 * 2; // 2 days in millis
  
  public static void main(String args[]) {
    if (args.length != 2) {
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
    
    PriorityScheduledExecutor scheduler = new PriorityScheduledExecutor(THREAD_COUNT, THREAD_COUNT, 1000, 
                                                                        TaskPriority.High, 1000, true);
    
    try {
      File[] origDestFileArray = destFolder.listFiles();
      File[] sourceFileArray = sourceFolder.listFiles();
      
      List<File> sourceFileList = makeValidSourceList(sourceFileArray, 
                                                      destFolder, 
                                                      origDestFileArray);
    
      Map<File, Future<?>> jobs = submitJobs(scheduler, 
                                             sourceFileList, 
                                             origDestFileArray, 
                                             destFolder);
      
      startKillThread(jobs, destFolder);
  
      // wait for all running processes to finish
      waitForJobs(jobs);
      
      deleteRemovedFiles(origDestFileArray, 
                         sourceFolder.listFiles(), // get new list, in case some were deleted while processing
                         destFolder);
    } finally {
      scheduler.shutdown();
    }
  }
  
  private static void startKillThread(Map<File, Future<?>> jobs, 
                                      File destFolder) {
    Thread killThread = new Thread(new TimeoutKiller(jobs, destFolder));
    killThread.setName("Timeout killer thread");
    killThread.setDaemon(true);
    killThread.start();
  }
  
  private static List<File> makeValidSourceList(File[] sourceFileArray, 
                                                File destFolder, File[] origDestFileArray) {
    List<File> sourceFileList = new ArrayList<File>(sourceFileArray.length);
    for (int i = 0; i < sourceFileArray.length; i++) {
      File sourceFile = sourceFileArray[i]; 
      if (sourceFile.isFile() && sourceFile.canRead()) {
        sourceFileList.add(sourceFile);
      } else if (! sourceFile.canRead()) {
        System.err.print("Can not read file: " + sourceFile.getAbsolutePath());
        
        File newFile = makeNewFile(destFolder, sourceFile);
        if (fileInArray(origDestFileArray, newFile)) {
          System.err.println("...will not remove already converted file: " + newFile.getAbsolutePath());
          
          // add so the already converted file is not removed
          sourceFileList.add(sourceFile);
        } else {
          System.err.println();
        }
      }
    }
    
    return sourceFileList;
  }
  
  private static Map<File, Future<?>> submitJobs(SubmitterSchedulerInterface scheduler, 
                                                 List<File> sourceFileList, 
                                                 File[] origDestFileArray, 
                                                 File destFolder) {
    Map<File, Future<?>> result = new HashMap<File, Future<?>>();
    AtomicInteger processedCount = new AtomicInteger();
    
    Iterator<File> it = sourceFileList.iterator();
    while (it.hasNext()) {
      File sourceFile = it.next();

      if (sourceFile.isDirectory()) {  // don't try and recursively copy/encode directories
        continue;
      }
      
      File newFile = makeNewFile(destFolder, sourceFile);
      
      if (fileInArray(origDestFileArray, newFile)) {
        // skip file, already added to processedFiles
        processedCount.incrementAndGet();
        
        continue;
      }
      
      Future<?> future = scheduler.submit(new ConverterWorker(processedCount, 
                                                              sourceFileList.size(), 
                                                              sourceFile, newFile));
      result.put(sourceFile, future);
    }
    
    return result;
  }

  private static File makeNewFile(File destFolder, File sourceFile) {
    String newName = maybeReplaceExt(sourceFile.getName());
    
    return new File(destFolder, newName);
  }

  private static String maybeReplaceExt(String name) {
    String extension = getExtension(name);
    if (extension.equalsIgnoreCase(DESIRED_EXTENSION)) {
      return name;
    } else {
      int index = name.lastIndexOf('.');
      if (index > 0) {
        return name.substring(0, index) + DESIRED_EXTENSION;
      } else {
        return name + DESIRED_EXTENSION;
      }
    }
  }
  
  private static String getExtension(String name) {
    int index = name.lastIndexOf('.');
    if (index > 0) {
      return name.substring(index);
    } else {
      return "";
    }
  }
  
  private static boolean fileInArray(File[] fileArray, File searchFile) {
    for (int i = 0; i < fileArray.length; i++) {
      if (fileArray[i].getAbsolutePath().equals(searchFile.getAbsolutePath())) {
        return true;
      }
    }
    
    return false;
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
  
  private static void deleteRemovedFiles(File[] origDestFileList, 
                                         File[] sourceFileArray, 
                                         File destFolder) {
    for (int i = 0; i < origDestFileList.length; i++) {
      boolean stillExists = false;
      for (int j = 0; j < sourceFileArray.length; j++) {
        String destFilePath = makeNewFile(destFolder, sourceFileArray[j]).getAbsolutePath();
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
  
  private static class ConverterWorker implements Runnable {
    private final long creationTime;
    private final long originalSize;
    private final AtomicInteger processedCount;
    private final int totalProcessCount;
    private final File sourceFile;
    private final File newFile;
    
    private ConverterWorker(AtomicInteger processedCount, 
                            int totalProcessCount, 
                            File sourceFile, File newFile) {
      originalSize = sourceFile.length();
      creationTime = Clock.lastKnownTimeMillis();
      
      this.processedCount = processedCount;
      this.totalProcessCount = totalProcessCount;
      this.sourceFile = sourceFile;
      this.newFile = newFile;
    }
    
    @Override
    public void run() {
      // verify file is not still growing before continuing
      if (! sizeStable(sourceFile, originalSize, creationTime)) {
        processedCount.incrementAndGet();
        
        return;
      }
      
      String extension = getExtension(sourceFile.getName());
      
      if (! extension.equalsIgnoreCase(DESIRED_EXTENSION)) {
        // convert
        if (VERBOSE) {
          /*System.out.println("Running: " + "mencoder " + sourceFile.getAbsolutePath() + 
                               " "  + FLAGS + " -o " + newFile.getAbsolutePath());*/
          System.out.println("Encoding " + extension + " file to: " + newFile.getAbsolutePath());
        }
        
        try {
          encodeFile(sourceFile, newFile);
        } catch (IOException e) {
          throw ExceptionUtils.makeRuntime(e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      } else {
        // copy the file
        if (VERBOSE) {
          System.out.println("Copying file to: " + newFile.getAbsolutePath());
        }
        
        try {
          copyFile(sourceFile, newFile);
        } catch (IOException e) {
          throw ExceptionUtils.makeRuntime(e);
        }
      }

      int count = processedCount.incrementAndGet();
         
      if (VERBOSE) {
        String percent = Double.toString(((count / (double)totalProcessCount)) * 100);
        System.out.println("Estimated % done: " + 
                             percent.substring(0, Math.min(percent.length() - 1, 5)) + "%" + 
                             " - ( " + count + " out of " + totalProcessCount + " )\n");
      }
    }
    
    private static boolean sizeStable(File file, 
                                      long originalSize, 
                                      long sizeCaptureTime) {
      long elapsedTime = Clock.lastKnownTimeMillis() - sizeCaptureTime;
      if (elapsedTime < FILE_SIZE_STABILITY_TIME_IN_MILLIS - 10) {  // only sleep if we need to wait at least 10ms
        try {
          Thread.sleep(FILE_SIZE_STABILITY_TIME_IN_MILLIS - elapsedTime);
        } catch (InterruptedException e1) {
          Thread.currentThread().interrupt();
        }
      }
      
      long newSize = file.length();
      if (newSize != originalSize) {
        System.out.println("Filesize changed for file: " + file + 
                             "..." + originalSize + "/" + newSize);
        
        return false;
      } else {
        return true;
      }
    }
    
    private static void encodeFile(File sourceFile, File destFile) throws IOException, InterruptedException {
      String[] command = {"/bin/bash",
                          "-c", 
                          "mencoder '" + sourceFile.getAbsolutePath() + '\'' + 
                            " "  + FLAGS + " -o '" + destFile.getAbsolutePath() + '\''
                         };
      Process p = Runtime.getRuntime().exec(command);

      byte[] buf = new byte[BUFFER_SIZE];
      InputStream stdOutIs = p.getInputStream();
      InputStream stdErrIs = p.getErrorStream();
      while (stdOutIs.read(buf) > -1 || 
             stdErrIs.read(buf) > -1) {
        // consume
      }
      
      if (p.waitFor() != 0) {
        throw new IllegalStateException("non-zero exit code for command: " + command[2]);
      }
    }
    
    private static void copyFile(File sourceFile, File destFile) throws IOException {
      InputStream in = null;
      OutputStream out = null;
      try {
        in = new FileInputStream(sourceFile);
        out = new FileOutputStream(destFile);

        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buf)) > 0){
          out.write(buf, 0, len);
        }
      } finally {
        try {
          if (in != null) {
              in.close();
          }
        } finally {
          if (out != null) {
            out.close();
          }
        }
      }
    }
  }
  
  private static class TimeoutKiller implements Runnable {
    private final Map<File, Future<?>> jobs;
    private final File destFolder;
    
    private TimeoutKiller(Map<File, Future<?>> jobs, 
                          File destFolder) {
      this.jobs = jobs;
      this.destFolder = destFolder;
    }
    
    @Override
    public void run() {
      try {
        Thread.sleep(MAX_RUN_TIME);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      
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
          File newFile = makeNewFile(destFolder, entry.getKey());
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
