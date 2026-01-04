/*
 * This file is part of Swingify ~ https://github.com/lunarydess/Library-Swingify
 * Copyright (C) 2025 ~ lunary
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package cc.lunary.lib_swingify;

import java.util.Arrays;

public enum OperatingSystem {
  WINDOWS, LINUX, MAC, SOLARIS, UNKNOWN;

  private static final String PROP_OS = System.getProperty("os.name").toLowerCase();

  // @formatter:off
  private static final OperatingSystem OS =
      (PROP_OS.contains("win"))                                                       ? WINDOWS :
      (PROP_OS.contains("mac") || PROP_OS.contains("osx"))                            ? MAC :
      (PROP_OS.contains("nix") || PROP_OS.contains("nux") || PROP_OS.contains("aix")) ? LINUX :
      (PROP_OS.contains("solaris"))                                                   ? SOLARIS :
      UNKNOWN;
  // @formatter:on

  private static final String libraryExtension = OS == WINDOWS ? "dll" : OS == MAC ? "dylib" : "SO";

  /**
   * @return the operating-system the jvm reported
   */
  public static OperatingSystem get() {
    return OS;
  }

  /**
   * @return the operating-system-string the jvm reported
   */
  public static String getPropString() {
    return PROP_OS;
  }

  /**
   * @return the native file-extension ending
   */
  public static String getLibExt() {
    return libraryExtension;
  }

  public enum Architecture {
    // @formatter:off
    X_86      (System.getProperty("os.arch").contains("x86_64") || System.getProperty("os.arch").contains("x86-64")),
    AMD_64    (System.getProperty("os.arch").contains("amd64"  )),
    ARM_64    (System.getProperty("os.arch").contains("aarch64")),
    ARM       (System.getProperty("os.arch").contains("armv7"  )),
    IA_64     (System.getProperty("os.arch").contains("ia64"   )),
    MIPS_64   (System.getProperty("os.arch").contains("mips64" )),
    MIPS      (System.getProperty("os.arch").contains("mips"   )),
    PPC_64    (System.getProperty("os.arch").contains("ppc64"  )),
    SPARC     (System.getProperty("os.arch").contains("sparcv9")),
    UNKNOWN(
        !X_86.is() &&
        !AMD_64.is() &&
        !ARM_64.is() &&
        !ARM.is() &&
        !IA_64.is() &&
        !MIPS_64.is() &&
        !MIPS.is() &&
        !PPC_64.is() &&
        !SPARC.is()
    );
    // @formatter:on

    private static final Architecture current = Arrays.stream(values())
        .filter(Architecture::is)
        .findFirst()
        .orElse(UNKNOWN);
    private final boolean is;

    Architecture(boolean is) {
      this.is = is;
    }

    public static boolean is64Bit() {
      return ARM_64.is() ||
          AMD_64.is() ||
          MIPS_64.is() ||
          PPC_64.is() ||
          SPARC.is() ||
          IA_64.is();
    }

    public static Architecture get() {
      return current;
    }

    public boolean is() {
      return this.is;
    }
  }
}
