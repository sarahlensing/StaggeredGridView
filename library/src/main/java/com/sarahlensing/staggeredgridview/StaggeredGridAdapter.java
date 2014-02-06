package com.sarahlensing.staggeredgridview;

import android.widget.BaseAdapter;

/**
 * Created by sarahlensing on 11/20/13.
 */


public abstract class StaggeredGridAdapter extends BaseAdapter {

    public abstract ItemSize getItemSize(int position);
}


