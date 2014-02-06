package com.sarahlensing.staggeredgridview;

import android.view.View;
import android.view.ViewGroup;

/**
 * Created by sarahlensing on 12/5/13.
 */
public abstract class StaggeredGridSectionAdapter extends StaggeredGridAdapter {

    public abstract ItemSize getSectionSize(int position);
    public abstract long getSectionID(int position);
    public abstract View getSectionView(int position, View convertView, ViewGroup parent);
    public abstract int getSectionCount();
    public abstract int getItemCountForSection(int section);

}
