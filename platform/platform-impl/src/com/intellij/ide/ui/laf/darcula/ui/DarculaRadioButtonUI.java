/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBGradientPaint;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.metal.MetalRadioButtonUI;
import javax.swing.text.View;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRadioButtonUI extends MetalRadioButtonUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new DarculaRadioButtonUI();
  }

  @Override
  public synchronized void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;

    Dimension size = c.getSize();

    Rectangle viewRect = new Rectangle(size);
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();
    AbstractButton b = (AbstractButton) c;
    //ButtonModel model = b.getModel();
    Font f = c.getFont();
    g.setFont(f);
    FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, f);

    String text = SwingUtilities.layoutCompoundLabel(
      c, fm, b.getText(), getDefaultIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, b.getIconTextGap());

    // fill background
    if(c.isOpaque()) {
      g.setColor(c.getBackground());
      g.fillRect(0,0, size.width, size.height);
    }


    paintIcon(c, g, viewRect, iconRect);
    drawText(b, g, text, textRect, fm);
  }

  protected void paintIcon(JComponent c, Graphics2D g, Rectangle viewRect, Rectangle iconRect) {
    Insets i = c.getInsets();
    viewRect.x += i.left;
    viewRect.y += i.top;
    viewRect.width -= (i.right + viewRect.x);
    viewRect.height -= (i.bottom + viewRect.y);

    g = (Graphics2D)g.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);

      int rad = JBUI.scale(5);
      int x = iconRect.x + (rad - (rad % 2 == 1 ? 1 : 0)) / 2;
      int y = iconRect.y + (rad - (rad % 2 == 1 ? 1 : 0)) / 2;
      int w = iconRect.width - rad;
      int h = iconRect.height - rad;

      g.translate(x, y);

      //noinspection UseJBColor
      JBGradientPaint ijGradient = new JBGradientPaint(c, new Color(0x4985e4), new Color(0x4074c9));

      //setup AA for lines
      boolean focus = c.hasFocus();
      boolean selected = ((AbstractButton)c).isSelected();

      if (UIUtil.isUnderDarcula() || !selected) {
        g.setPaint(UIUtil.getGradientPaint(0, 0, ColorUtil.shift(c.getBackground(), 1.5),
                                           0, c.getHeight(), ColorUtil.shift(c.getBackground(), 1.2)));
      } else {
        //noinspection UseJBColor
        g.setPaint(ijGradient);
      }

      if (!UIUtil.isUnderDarcula() && selected) {
        GraphicsConfig fillOvalConf = new GraphicsConfig(g);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.fillOval(0, JBUI.scale(1), w, h);
        fillOvalConf.restore();
      } else {
        if (focus) {
          g.fillOval(0, JBUI.scale(1), w, h);
        } else if (c.isEnabled()){
          g.fillOval(0, JBUI.scale(1), w - JBUI.scale(1), h - JBUI.scale(1));
        }
      }

      if (focus) {
        if (JBUI.isPixHiDPI(c)) {
          DarculaUIUtil.paintFocusOval(g, JBUI.scale(1), JBUI.scale(1) + 1, w - JBUI.scale(2), h - JBUI.scale(2));
        } else {
          DarculaUIUtil.paintFocusOval(g, 0, JBUI.scale(1), w, h);
        }
      } else {
        if (UIUtil.isUnderDarcula()) {
          if (c.isEnabled()) {
            g.setPaint(UIUtil.getGradientPaint(w / 2, 1, Gray._160.withAlpha(90), w / 2, h, Gray._100.withAlpha(90)));
            g.drawOval(0, JBUI.scale(1) + 1, w - 1, h - 1);

            g.setPaint(Gray._40.withAlpha(200));
            g.drawOval(0, JBUI.scale(1), w - 1, h - 1);
          } else {
            g.setColor(Gray.x58);
            g.drawOval(0, JBUI.scale(1), w - 1, h - 1);
          }
        } else {
          g.setPaint(selected ? ijGradient : c.isEnabled() ? Gray._30 : Gray._130);
          if (!selected) {
            g.drawOval(0, JBUI.scale(1) + 1, w - 1, h - 1);
          }
        }
      }

      if (selected) {
        boolean enabled = c.isEnabled();
        int yOff = 1 + JBUI.scale(1);

        if (!UIUtil.isUnderDarcula() || enabled) {
          g.setColor(UIManager.getColor(enabled ? "RadioButton.darcula.selectionEnabledShadowColor" : "RadioButton.darcula.selectionDisabledShadowColor"));
          g.fillOval(w/2 - rad/2, h/2 - rad/2 + yOff , rad, rad);
        }

        g.setColor(UIManager.getColor(enabled ? "RadioButton.darcula.selectionEnabledColor" : "RadioButton.darcula.selectionDisabledColor"));
        g.fillOval(w/2 - rad/2, h/2 - rad/2 - 1 + yOff, rad, rad);
      }
    } finally {
      g.dispose();
    }
  }

  protected void drawText(AbstractButton b, Graphics2D g, String text, Rectangle textRect, FontMetrics fm) {
    // Draw the Text
    if(text != null) {
      View v = (View) b.getClientProperty(BasicHTML.propertyKey);
      if (v != null) {
        v.paint(g, textRect);
      } else {
        int mnemIndex = b.getDisplayedMnemonicIndex();
        if(b.isEnabled()) {
          // *** paint the text normally
          g.setColor(b.getForeground());
        } else {
          // *** paint the text disabled
          g.setColor(getDisabledTextColor());
        }
        SwingUtilities2.drawStringUnderlineCharAt(b, g, text,
                                                  mnemIndex, textRect.x, textRect.y + fm.getAscent());
      }
    }

    if(b.hasFocus() && b.isFocusPainted() &&
       textRect.width > 0 && textRect.height > 0 ) {
        paintFocus(g, textRect, b.getSize());
    }
  }

  @Override
  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {

  }

  @Override
  public Icon getDefaultIcon() {
    return JBUI.scale(EmptyIcon.create(20)).asUIResource();
  }
}
