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
package cc.lunary.swingify;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.intellijthemes.*;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

public enum Themes {
  // @formatter:off
  LIGHT ("Light", FlatLightLaf ::new),
  DARK  ("Dark",  FlatDarkLaf  ::new),

  ARC             ("Arc - Default",     FlatArcIJTheme           ::new),
  ARC_ORANGE      ("Arc - Orange",      FlatArcOrangeIJTheme     ::new),
  ARC_DARK        ("Arc - Dark",        FlatArcDarkIJTheme       ::new),
  ARC_DARK_ORANGE ("Arc - Dark Orange", FlatArcDarkOrangeIJTheme ::new),

  GRADIANTO_MIDNIGHT_BLUE ("Gradianto - Midnight Blue", FlatGradiantoMidnightBlueIJTheme ::new),
  GRADIANTO_DEEP_OCEAN    ("Gradianto - Deep Ocean",    FlatGradiantoDeepOceanIJTheme    ::new),
  GRADIANTO_DARK_FUCHSIA  ("Gradianto - Dark Fuchsia",  FlatGradiantoDarkFuchsiaIJTheme  ::new),
  GRADIANTO_NATURE_GREEN  ("Gradianto - Nature Green",  FlatGradiantoNatureGreenIJTheme  ::new),

  GRUVBOX_DARK_HARD   ("Gruvbox - Dark Hard",   FlatGruvboxDarkHardIJTheme   ::new),

  INTELLIJ ("IntelliJ", FlatIntelliJLaf ::new),
  DARCULA  ("Darcula",  FlatDarculaLaf  ::new),

  MATERIAL_DESIGN_DARK    ("Material - Design Dark",     FlatMaterialDesignDarkIJTheme ::new),
  MATERIAL_ARC_DARK       ("Material - Arc Dark",        FlatArcDarkIJTheme            ::new),
  MATERIAL_ATOM_ONE_DARK  ("Material - Atom One Dark",   FlatMTAtomOneDarkIJTheme        ::new),
  MATERIAL_ATOM_ONE_LIGHT ("Material - Atom One Light",  FlatMTAtomOneLightIJTheme       ::new),
  MATERIAL_DRACULA        ("Material - Dracula",         FlatDraculaIJTheme            ::new),
  MATERIAL_GITHUB         ("Material - GitHub",          FlatMTGitHubIJTheme             ::new),
  MATERIAL_GITHUB_DARK    ("Material - GitHub Dark",     FlatMTGitHubDarkIJTheme         ::new),
  MATERIAL_LIGHT_OWL      ("Material - Light Owl",       FlatMTLightOwlIJTheme           ::new),
  MATERIAL_DARKER         ("Material - Darker",          FlatMTMaterialDarkerIJTheme     ::new),
  MATERIAL_DEEP_OCEAN     ("Material - Deep Ocean",      FlatMTMaterialDeepOceanIJTheme  ::new),
  MATERIAL_LIGHTER        ("Material - Lighter",         FlatMTMaterialLighterIJTheme    ::new),
  MATERIAL_OCEANIC        ("Material - Oceanic",         FlatMTMaterialOceanicIJTheme    ::new),
  MATERIAL_PALENIGHT      ("Material - Palenight",       FlatMTMaterialPalenightIJTheme  ::new),
  MATERIAL_MONOKAI_PRO    ("Material - Monokai Pro",     FlatMonokaiProIJTheme         ::new),
  MATERIAL_MOONLIGHT      ("Material - Moonlight",       FlatMTMoonlightIJTheme          ::new),
  MATERIAL_NIGHT_OWL      ("Material - Night Owl",       FlatMTNightOwlIJTheme           ::new),
  MATERIAL_SOLARIZED_DARK ("Material - Solarized Dark",  FlatSolarizedDarkIJTheme      ::new),
  MATERIAL_SOLARIZED_LIGH ("Material - Solarized Light", FlatSolarizedLightIJTheme     ::new),

  SOLARIZED_DARK  ("Solarized - Dark",  FlatSolarizedDarkIJTheme  ::new),
  SOLARIZED_LIGHT ("Solarized - Light", FlatSolarizedLightIJTheme ::new),

  CARBON        ("Carbon",        FlatCarbonIJTheme       ::new),
  COBALT2       ("Cobalt 2",      FlatCobalt2IJTheme      ::new),
  CYAN_LIGHT    ("Cyan Light",    FlatCyanLightIJTheme    ::new),
  DARK_FLAT     ("Dark Flat",     FlatDarkFlatIJTheme     ::new),
  DARK_PURPLE   ("Dark purple",   FlatDarkPurpleIJTheme   ::new),
  DRACULA       ("Dracula",       FlatDraculaIJTheme      ::new),
  GRAY          ("Gray",          FlatGrayIJTheme         ::new),
  HIBERBEE_DARK ("Hiberbee Dark", FlatHiberbeeDarkIJTheme ::new),
  HIGH_CONTRAST ("High Contrast", FlatHighContrastIJTheme ::new),
  LIGHT_FLAT    ("Light Flat",    FlatLightFlatIJTheme    ::new),
  MONOCAI       ("Monocai",       FlatMonocaiIJTheme      ::new),
  NORD          ("Nord",          FlatNordIJTheme         ::new),
  ONE_DARK      ("One Dark",      FlatOneDarkIJTheme      ::new),
  SPACEGRAY     ("Spacegray",     FlatSpacegrayIJTheme    ::new),
  VUESION       ("Vuesion",       FlatVuesionIJTheme      ::new),
  XCODE         ("XCode",         FlatXcodeDarkIJTheme    ::new);
  // @formatter:on

  private static final File SCHEME_FILE = new File(System.getProperty(
      "user.home",
      "~/.swingbuilder"
  ), "./swing.theme.txt");
  private static Themes globalTheme = DARK;

  static {
    if (!SCHEME_FILE.exists()) {
      SCHEME_FILE.mkdirs();
      SCHEME_FILE.delete();
      try {
        if (!SCHEME_FILE.createNewFile()) throw new RuntimeException();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private final String friendlyName;
  private final Supplier<FlatLaf> factory;

  Themes(
      final String friendlyName,
      final Supplier<FlatLaf> factory
  ) {
    this.friendlyName = friendlyName;
    this.factory = factory;
  }

  public static void select(final @NotNull Themes theme) {
    select(theme, true);
  }

  public static void select(
      final @NotNull Themes theme,
      final boolean update
  ) {
    globalTheme = theme;
    applyGlobal(update);
  }

  public static void applyGlobal() {
    applyGlobal(false);
  }

  public static void applyGlobal(boolean update) {
    if (FlatLaf.setup(globalTheme.getFactory().get()) && update) {
      FlatLaf.updateUI();
    }
  }

  public static @Nullable Themes byName(final @NotNull String name) {
    return byName(name, true);
  }

  public static @Nullable Themes byName(
      final @NotNull String name,
      final boolean ignoreCase
  ) {
    return Arrays.stream(values())
        .filter(ignoreCase ?
            t -> t.friendlyName.equalsIgnoreCase(name) :
            t -> t.friendlyName.equals(name))
        .findFirst().orElse(null);
  }

  public static Themes getGlobalTheme() {
    return globalTheme;
  }

  public String getFriendlyName() {
    return this.friendlyName;
  }

  public Supplier<FlatLaf> getFactory() {
    return this.factory;
  }
}
