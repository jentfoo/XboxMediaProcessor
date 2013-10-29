package com.jentfoo;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.threadly.concurrent.SubmitterSchedulerInterface;

public interface ConverterInterface {
  public String getProducedExtesion();

  public Map<File, Future<?>> submitJobs(SubmitterSchedulerInterface makeSubPool,
                                         List<File> sourceFileList,
                                         File destFolder);
}
