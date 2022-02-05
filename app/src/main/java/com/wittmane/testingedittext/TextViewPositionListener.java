package com.wittmane.testingedittext;

public interface TextViewPositionListener {
    public void updatePosition(int parentPositionX, int parentPositionY,
                               boolean parentPositionChanged, boolean parentScrolled);
}
