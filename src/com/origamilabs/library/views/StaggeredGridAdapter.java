package com.origamilabs.library.views;

import android.widget.BaseAdapter;
import android.widget.ListAdapter;

/**
 * Created by sarahlensing on 11/20/13.
 */


public abstract class StaggeredGridAdapter extends BaseAdapter {

    public abstract ItemSize getItemSize(int position);

}


