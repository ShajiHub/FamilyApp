package com.shajitha.familyapp;

/** One row in the checklist: either a section header (image and title) or an item to tick. */
public class ChecklistItems {

    public static final int listLayout = 1;
    public static final int imageLayout = 2;

    private final int chooseLayout;
    private final String text;
    private final int img;
    private final String dispText;
    private String section = "";

    // An item to tick.
    public ChecklistItems(int chooseLayout, String text) {
        this.chooseLayout = chooseLayout;
        this.text = text;
        this.img = 0;
        this.dispText = null;
    }

    // A section header with an image.
    public ChecklistItems(int viewType, int img, String dispText) {
        this.chooseLayout = viewType;
        this.img = img;
        this.dispText = dispText;
        this.text = null;
    }

    public String getText() {
        return text;
    }

    public int getimg() {
        return img;
    }

    public String getdispText() {
        return dispText;
    }

    public int getchooseLayout() {
        return chooseLayout;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    /** Unique name used to remember whether this item is ticked. */
    public String getKey() {
        return section + "|" + text;
    }
}
