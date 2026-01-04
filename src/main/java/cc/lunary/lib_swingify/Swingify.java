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

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

public final class Swingify<F extends Frame> {
  private final @NotNull SwingItem<F> frame;

  public static void init() {
    switch (OperatingSystem.get()) {
      case LINUX -> {
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
      }

      case MAC -> {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Swing Builder");
        System.setProperty("apple.awt.application.appearance", "system");
      }
    }
  }

  public Swingify(@NotNull SwingItem<F> frame) {
    this(frame, "Swing Builder");
  }

  public Swingify(@NotNull SwingItem<F> frame, Consumer<SwingItem<F>> handler) {
    this(frame, "Swing Builder", Themes.ARC_DARK, handler);
  }

  public Swingify(@NotNull SwingItem<F> frame, Themes theme) {
    this(frame, "Swing Builder", theme);
  }

  public Swingify(
      @NotNull SwingItem<F> frame,
      Themes theme,
      Consumer<SwingItem<F>> handler
  ) {
    this(frame, "Swing Builder", theme, handler);
  }

  public Swingify(@NotNull SwingItem<F> frame, String name) {
    this(frame, name, Themes.ARC_DARK);
  }

  public Swingify(
      @NotNull SwingItem<F> frame,
      String name,
      Consumer<SwingItem<F>> handler
  ) {
    this(frame, name, Themes.ARC_DARK, handler);
  }

  public Swingify(
      @NotNull SwingItem<F> frame,
      String name,
      Themes theme
  ) {
    this(frame, name, theme, item -> System.exit(0));
  }

  public Swingify(
      @NotNull SwingItem<F> frame, String name,
      Themes theme, Consumer<SwingItem<F>> handler
  ) {
    this.frame = frame;
    if (OperatingSystem.get() == OperatingSystem.MAC)
      System.setProperty("apple.awt.application.name", name);
    Themes.select(theme);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
      if (event.getKeyCode() != KeyEvent.VK_F11) return false;
      if (event.paramString().startsWith("KEY_RELEASED")) {
        var defaultDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        defaultDevice.setFullScreenWindow(defaultDevice.getFullScreenWindow() == null ? frame.component() : null);
      }
      return false;
    });
    this.frame.component().addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        handler.accept(frame);
      }
    });
    if (!(this.frame.component() instanceof JFrame jFrame)) return;
    jFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
  }

  public void show() {
    this.frame().show();
  }

  public void hide() {
    this.frame().hide();
  }

  /**
   * @return the frame we built.
   */
  public @NotNull SwingItem<? extends Frame> frame() {
    return this.frame;
  }
}
