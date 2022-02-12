package com.wittmane.testingedittext.editor;

public interface TextViewPositionListener {
    void updatePosition(int parentPositionX, int parentPositionY,
                        boolean parentPositionChanged, boolean parentScrolled);
}
