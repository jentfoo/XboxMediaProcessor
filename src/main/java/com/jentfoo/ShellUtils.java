package com.jentfoo;

import java.io.File;

public class ShellUtils {
  private static final String DASH_PATH = "/bin/dash";
  private static final String BASH_PATH = "/bin/dash";
  private static final String DEFAULT_SHELL_COMMAND_FLAG = "-c";
  private static final String DEFAULT_SHELL;
  
  static {
    File shell = new File("/bin/dash");
    if (shell.exists() && shell.canExecute()) {
      DEFAULT_SHELL = DASH_PATH;
    } else {
      DEFAULT_SHELL = BASH_PATH;
    }
  }
  
  public static String getDefaultShell() {
    return DEFAULT_SHELL;
  }
  
  public static String getDefaultShellCommandFlag() {
    return DEFAULT_SHELL_COMMAND_FLAG;
  }
}
