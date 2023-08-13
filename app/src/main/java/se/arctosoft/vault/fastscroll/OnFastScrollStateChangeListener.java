package se.arctosoft.vault.fastscroll;

// From https://github.com/timusus/RecyclerView-FastScroll/blob/master/recyclerview-fastscroll/src/main/java/com/simplecityapps/recyclerview_fastscroll/interfaces/OnFastScrollStateChangeListener.java
public interface OnFastScrollStateChangeListener {

    /**
     * Called when fast scrolling begins
     */
    void onFastScrollStart();

    /**
     * Called when fast scrolling ends
     */
    void onFastScrollStop();
}