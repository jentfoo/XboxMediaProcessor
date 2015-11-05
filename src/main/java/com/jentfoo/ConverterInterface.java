package com.jentfoo;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.threadly.concurrent.SubmitterScheduler;

public interface ConverterInterface {
  public String getProducedExtesion();

  public Map<File, Future<?>> submitJobs(SubmitterScheduler makeSubPool,
                                         List<File> sourceFileList,
                                         File destFolder);
}
