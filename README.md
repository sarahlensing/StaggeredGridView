StaggeredGridView
=======

## Introduction

This is a fork of the modified version of Android's experimental StaggeredGridView that was originally posted [here](https://github.com/maurycyw/StaggeredGridView). 

The internal logic has been completely rewritten to support both horizontal and vertical orientations. This version also adds support for sections and preloading pages. It recycles its views and uses the common Adapter pattern found in common Android UI elements such as ListView.

This version of StaggeredGridView is optimized for views where the size of each element is known. It's adapter adds a method to retrieve the size of each element in the grid. This is used to enhance scroll performance as well as to position the item on screen.

## Setup

####1. Create a folder in the project's root directory to hold third party libraries:

```
mkdir lib
```

####2. Clone the repository into the library directory:

```
cd lib
```

```
git clone git@github.com:sarahlensing/StaggeredGridView.git
```

-or-

Add the repository as a submodule:

```
git submodule add git@github.com:sarahlensing/StaggeredGridView.git lib/StaggeredGridView
```

####3. Add as a dependency to your project

#####Via Gradle:

```
dependencies {
    compile 'com.android.support:appcompat-v7:+'
    compile project(':submodules:StaggeredGridView:library')
}
```

-or-
#####Library project:
If you are not using Gradle, add the StaggeredGridView as a [library project dependency](http://stackoverflow.com/questions/16588064/how-do-i-add-a-library-project-to-the-android-studio)


<em>Note: Gradle artifact will be published soon so that you will not have to worry about steps 1 and 2 if you are using Gradle or Maven.</em>

## Usage

StaggeredGridView can be added as a custom view to any layout. 

Attributes supported:
 
 * <strong>itemMargin</strong> : determines the margin between items in the grid
 * <strong>numPagesToPreload</strong> : determines the number of pages offscreen in either direction to preload
 * <strong>gridOrientation</strong> : determines the direction in which items are laid out: horizontally or vertically

```
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    xmlns:staggered="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:id="@+id/mainLayout">

    <com.samsung.staggeredgridview.StaggeredGridView
        android:id="@+id/staggeredGridView1"
		staggered:gridOrientation="horizontal"
		staggered:itemMargin="20dp"
		staggered:numPagesToPreload="2"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</LinearLayout>
```

To feed the StaggeredGridView with data, create an adapter:

<strong>StaggeredGridAdapter</strong>


            StaggeredGridAdapter mGridAdapter = new StaggeredGridAdapter() {

            @Override
            public ItemSize getItemSize(int position) {
                return mItemSizes.get(position);
            }

            @Override
            public int getCount() {
                return mItemSizes.size();
            }

            @Override
            public Object getItem(int position) {
                return position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewHolder holder;
                if (convertView == null) {
                    LayoutInflater layoutInflator = LayoutInflater.from(getActivity().getBaseContext());
                    convertView = layoutInflator.inflate(R.layout.grid_item_view, null);
                    holder = new ViewHolder();
                    holder.gridItemView = (GridItemView) convertView;
                    holder.titleView = (TextView) convertView.findViewById(R.id.titleView);
                    holder.subtitleView = (TextView) convertView.findViewById(R.id.subtitleView);
                    convertView.setTag(holder);
                }
                else {
                    holder = (ViewHolder) convertView.getTag();
                }
                ItemSize itemSize = mItemSizes.get(position);
                holder.gridItemView.itemSize = itemSize;
                holder.gridItemView.setBackgroundColor(nextRandomColor());
                holder.titleView.setText(String.valueOf(position));
                holder.subtitleView.setText(String.valueOf(itemSize.width) + "x" + String.valueOf(itemSize.height));
                return convertView;
            }
        };



<strong>StaggeredGridSectionAdapter</strong>


          StaggeredGridSectionAdapter mGridSectionAdapter = new StaggeredGridSectionAdapter() {

           @Override
           public ItemSize getItemSize(int position) {
               return mItemSizes.get(position);
           }

           @Override
           public int getCount() {
               return mItemSizes.size();
           }

           @Override
           public Object getItem(int position) {
               return position;
           }

           @Override
           public long getItemId(int position) {
               return position;
           }

           @Override
           public View getView(int position, View convertView, ViewGroup parent) {
               ViewHolder holder;
               if (convertView == null) {
                   LayoutInflater layoutInflator = LayoutInflater.from(getActivity().getBaseContext());
                   convertView = layoutInflator.inflate(R.layout.grid_item_view, null);
                   holder = new ViewHolder();
                   holder.gridItemView = (GridItemView) convertView;
                   holder.titleView = (TextView) convertView.findViewById(R.id.titleView);
                   holder.subtitleView = (TextView) convertView.findViewById(R.id.subtitleView);
                   convertView.setTag(holder);
               }
               else {
                   holder = (ViewHolder) convertView.getTag();
               }
               ItemSize itemSize = mItemSizes.get(position);
               holder.gridItemView.itemSize = itemSize;
               holder.gridItemView.setBackgroundColor(nextRandomColor());
               holder.titleView.setText(String.valueOf(position));
               holder.subtitleView.setText(String.valueOf(itemSize.width) + "x" + String.valueOf(itemSize.height));
               return convertView;
           }

           @Override
           public ItemSize getSectionSize(int position) {
               return mSectionSizes.get(position);
           }

           @Override
           public long getSectionID(int position) {
               return position;
           }

           @Override
           public View getSectionView(int position, View convertView, ViewGroup parent) {
               SectionViewHolder holder;
               if (convertView == null) {
                   LayoutInflater layoutInflator = LayoutInflater.from(getActivity().getBaseContext());
                   convertView = layoutInflator.inflate(R.layout.grid_section_view, null);
                   holder = new SectionViewHolder();
                   holder.gridItemView = (GridItemView) convertView;
                   holder.titleView = (TextView) convertView.findViewById(R.id.titleView);
                   convertView.setTag(holder);
               }
               else {
                   holder = (SectionViewHolder) convertView.getTag();
               }
               ItemSize itemSize = mSectionSizes.get(position);
               holder.gridItemView.itemSize = itemSize;
               holder.gridItemView.setBackgroundColor(Color.BLUE);
               holder.titleView.setText(String.valueOf(position));
               return convertView;
           }

           @Override
           public int getSectionCount() {
               return mSectionSizes.size();
           }

           @Override
           public int getItemCountForSection(int section) {
               return 10;
           }

           @Override
           public int getItemViewType(int position) {
               return 0;
           }

           @Override
           public int getViewTypeCount() {
               return 1;
           }
       };


Then call:

```
mGridView.setAdapter(mGridSectionAdapter);
```

## Tests

No tests have been written however I will test this View manually with 2.2.2+ devices and upload a demo project. Please report any issues.


## TODO:

* Implement sticky headers
* Support rearranging
* Support multiple selection
* Develop tests


