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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record SwingItem<C extends Component>(C component) {
  public void hide() {
    if (this.component instanceof Container container && container.isVisible()) {
      SwingUtilities.invokeLater(() -> container.setVisible(false));
    }
  }

  public void show() {
    if (this.component instanceof Container container && !container.isVisible()) {
      SwingUtilities.invokeLater(() -> container.setVisible(true));
    }
  }

  public void toggle() {
    if (this.component.isVisible()) this.hide();
    else this.show();
  }

  public enum Mouse {CLICK, PRESS, RELEASE, ENTER, EXIT, WHEEL, MOVE, DRAG}

  public static class Builder<C extends Component> {
    @SuppressWarnings("unchecked")
    private static final Consumer<MouseEvent>[] EMPTY_EVENTS = new Consumer[0];
    private final C component;

    private final IdentityHashMap<Mouse, Consumer<MouseEvent>[]> mouseEvents = new IdentityHashMap<>();
    private final MouseAdapter mouseEventsAdapter = new MouseAdapter() {
      public @Override void mouseClicked(MouseEvent event) {
        var handlers = mouseEvents.getOrDefault(Mouse.CLICK, EMPTY_EVENTS);
        if (handlers.length == 0) return;
        SwingUtilities.invokeLater(
            () -> Arrays.stream(handlers).forEach(handler -> handler.accept(event))
        );
      }

      public @Override void mousePressed(MouseEvent event) {
        var handlers = mouseEvents.getOrDefault(Mouse.PRESS, EMPTY_EVENTS);
        if (handlers.length == 0) return;
        SwingUtilities.invokeLater(
            () -> Arrays.stream(handlers).forEach(handler -> handler.accept(event))
        );
      }

      public @Override void mouseReleased(MouseEvent event) {
        var handlers = mouseEvents.getOrDefault(Mouse.RELEASE, EMPTY_EVENTS);
        if (handlers.length == 0) return;
        SwingUtilities.invokeLater(
            () -> Arrays.stream(handlers).forEach(handler -> handler.accept(event))
        );
      }

      public @Override void mouseEntered(MouseEvent event) {
        var handlers = mouseEvents.getOrDefault(Mouse.ENTER, EMPTY_EVENTS);
        if (handlers.length == 0) return;
        SwingUtilities.invokeLater(
            () -> Arrays.stream(handlers).forEach(handler -> handler.accept(event))
        );
      }

      public @Override void mouseExited(MouseEvent event) {
        var handlers = mouseEvents.getOrDefault(Mouse.EXIT, EMPTY_EVENTS);
        if (handlers.length == 0) return;
        SwingUtilities.invokeLater(
            () -> Arrays.stream(handlers).forEach(handler -> handler.accept(event))
        );
      }

      public @Override void mouseWheelMoved(MouseWheelEvent event) {
        var handlers = mouseEvents.getOrDefault(Mouse.WHEEL, EMPTY_EVENTS);
        if (handlers.length == 0) return;
        SwingUtilities.invokeLater(
            () -> Arrays.stream(handlers).forEach(handler -> handler.accept(event))
        );
      }

      public @Override void mouseDragged(MouseEvent event) {
        var handlers = mouseEvents.getOrDefault(Mouse.MOVE, EMPTY_EVENTS);
        if (handlers.length == 0) return;
        SwingUtilities.invokeLater(
            () -> Arrays.stream(handlers).forEach(handler -> handler.accept(event))
        );
      }

      public @Override void mouseMoved(MouseEvent event) {
        var handlers = mouseEvents.getOrDefault(Mouse.MOVE, EMPTY_EVENTS);
        if (handlers.length == 0) return;
        SwingUtilities.invokeLater(
            () -> Arrays.stream(handlers).forEach(handler -> handler.accept(event))
        );
      }
    };

    public Builder(Supplier<C> factory) {
      this.component = factory.get();
    }

    public Builder<C> size(int width, int height) {
      return this.size(new Dimension(width, height));
    }

    public Builder<C> size(Point point) {
      return this.size(point.x, point.y);
    }

    public Builder<C> size(Dimension dimension) {
      this.component.setPreferredSize(dimension);
      this.component.setSize(dimension);
      return this;
    }
    
    public Builder<C> sizeDefault() {
      return this.sizeDefault(0.5D);
    }

    public Builder<C> sizeDefault(double factor) {
      Dimension dimension;
      try {
        dimension = Toolkit.getDefaultToolkit().getScreenSize();
        dimension.setSize(dimension.getWidth() * factor, dimension.getHeight() * factor);
      } catch (final Throwable ignored) {
        dimension = new Dimension(320, 180);
      }
      this.component.setSize(dimension);
      return this;
    }

    public <E extends MouseEvent> Builder<C> mouse(Mouse type, Runnable handler) {
      return this.mouse(type, event -> handler.run());
    }

    @SuppressWarnings("unchecked")
    public <E extends MouseEvent> Builder<C> mouse(Mouse type, Consumer<E> handler) {
      this.mouseEvents.putIfAbsent(type, EMPTY_EVENTS);
      var events = this.mouseEvents.get(type);
      var eventsNew = new Consumer[events.length + 1];
      System.arraycopy(events, 0, eventsNew, 0, events.length);
      eventsNew[eventsNew.length - 1] = handler;
      this.mouseEvents.put(type, eventsNew);
      return this;
    }

    public Builder<C> location(Point point) {
      return this.location(point.x, point.y);
    }

    public Builder<C> location(Dimension dimension) {
      return this.location(dimension.width, dimension.height);
    }

    public Builder<C> location(int width, int height) {
      this.component.setLocation(width, height);
      return this;
    }


    public <O extends Component> Builder<C> locationRelativeTo(final C component) {
      if (this.component instanceof Window window) {
        window.setLocationRelativeTo(component);
      }
      return this;
    }

    public Builder<C> locationDefault() {
      if (this.component instanceof Window window) {
        window.setLocationRelativeTo(null);
        window.setLocationByPlatform(true);
      }
      return this;
    }

    public Builder<C> resizable(boolean resizable) {
      if (this.component instanceof Frame frame) {
        frame.setResizable(resizable);
      }
      return this;
    }

    public Builder<C> layout(LayoutManager layout) {
      if (this.component instanceof Container container) {
        container.setLayout(layout);
      }
      return this;
    }

    public Builder<C> background(Color color) {
      this.component.setBackground(color);
      return this;
    }

    public Builder<C> paintBorder(boolean state) {
      if (this.component instanceof AbstractButton button) {
        button.setBorderPainted(state);
      }
      return this;
    }

    public <O extends Component> Builder<C> add(@NotNull SwingItem<O> item) {
      return this.add((String) null, item);
    }

    public <O extends Component> Builder<C> add(
        @NotNull SwingItem<O> item,
        @NotNull String constraints
    ) {
      if (this.component instanceof Container container) {
        container.add(item.component, constraints);
      }
      return this;
    }

    public <O extends Component> Builder<C> add(
        @Nullable String name,
        @NotNull SwingItem<O> item
    ) {
      if (this.component instanceof Container container) {
        container.add(name, item.component);
      }
      return this;
    }

    public <O extends Component> Builder<C> add(@NotNull SwingItem.List<?, ?> item) {
      return this.add((String) null, item);
    }

    public <O extends Component> Builder<C> add(
        @NotNull SwingItem.List<?, ?> item,
        @Nullable Object constraints
    ) {
      return this.add(item, constraints, -1);
    }

    @SuppressWarnings("rawtypes")
    public <O extends Component> Builder<C> add(
        @NotNull SwingItem.List<?, ?> item,
        @Nullable Object constraints,
        int index
    ) {
      if (this.component instanceof JList list) {
        list.add(item.panel, constraints, index);
      }
      return this;
    }

    public <O extends Component> Builder<C> add(
        @Nullable String name,
        @NotNull SwingItem.List<?, ?> item
    ) {
      if (this.component instanceof Container container) {
        container.add(name, item.panel());
      }
      return this;
    }

    public <O extends Component> Builder<C> add(
        @NotNull SwingItem<O> item,
        @Nullable Object constraints
    ) {
      return this.add(item, constraints, -1);
    }

    public <O extends Component> Builder<C> add(
        @NotNull SwingItem<O> item,
        @Nullable Object constraints,
        int index
    ) {
      if (this.component instanceof Container container) {
        container.add(item.component, constraints, index);
      }
      return this;
    }

    public Builder<C> popup(@NotNull PopupMenu menu) {
      if (this.component instanceof Container container) {
        container.add(menu);
      }
      return this;
    }

    public <B extends JMenuBar> Builder<C> bar(SwingItem<B> bar) {
      if (this.component instanceof JFrame jFrame) {
        jFrame.setJMenuBar(bar.component);
      }
      return this;
    }

    public C component() {
      return this.component;
    }

    public SwingItem<C> build() {
      this.component.addMouseListener(mouseEventsAdapter);
      return new SwingItem<>(this.component);
    }
  }

  @SuppressWarnings("TypeParameterExplicitlyExtendsObject")
  public record List<O extends Object, L extends JList<O>>(JPanel panel) {
    public enum Scroll {NONE, VERTICAL, HORIZONTAL, BOTH}

    public static final class Builder<O extends Object, L extends JList<O>> {
      private final L list;
      private final JScrollPane pane;

      public Builder(Supplier<L> factory) {
        this.list = factory.get();
        this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.pane = new JScrollPane();
      }

      public Builder<O, L> scroll(Scroll scroll) {
        var isHorizontal = scroll == Scroll.HORIZONTAL;
        var isVertical = scroll == Scroll.VERTICAL;
        var isBoth = scroll == Scroll.BOTH;
        list.setLayoutOrientation(isHorizontal ? JList.HORIZONTAL_WRAP : JList.VERTICAL);
        if (isHorizontal || isBoth)
          pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        if (isVertical || isBoth)
          pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        return this;
      }

      public final @SafeVarargs Builder<O, L> items(O... items) {
        var model = new DefaultListModel<O>();
        model.addAll(Arrays.stream(items).toList());
        list.setModel(model);
        return this;
      }

      public SwingItem.List.Builder<O, L> select(BiConsumer<L, ListSelectionEvent> handler) {
        this.list.addListSelectionListener(event -> SwingUtilities.invokeLater(
            () -> handler.accept(this.list, event)
        ));
        return this;
      }

      public SwingItem.List<O, L> build() {
        this.pane.add(this.list);
        this.pane.setViewportView(this.list);
        var panel = new JPanel(new BorderLayout());
        panel.add(this.pane);
        return new SwingItem.List<>(panel);
      }
    }
  }
}
