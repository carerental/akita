package com.alibaba.akita.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: justin
 * Date: 12-4-8
 * Time: 下午2:42
 *
 * @author Justin Yang
 */
public abstract class AkBaseAdapter<T> extends BaseAdapter {

    protected ArrayList<T> mData = new ArrayList();
    protected LayoutInflater mInflater;
    protected Context mContext;

    public AkBaseAdapter(Context c) {
        mContext = c;
        mInflater = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void addItem(final T item) {
        mData.add(item);
        notifyDataSetChanged();
    }

    public void addItem(int idx, final T item) {
        mData.add(idx, item);
        notifyDataSetChanged();
    }

    public void clearItems() {
        mData.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public T getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
    ViewHolder holder = null;
    if (convertView == null) {
        convertView = mInflater.inflate(R.layout.list_item2, null);
        holder = new ViewHolder();
        holder.textView = (TextView)convertView.findViewById(R.id.text);
        holder.imageView = (RemoteImageView)convertView.findViewById(R.id.ri);
        convertView.setTag(holder);
    } else {
        holder = (ViewHolder)convertView.getTag();
    }
    holder.textView.setText(mData.get(position).status.text);
    if (mData.get(position).status.original_pic!=null) {
        holder.imageView.setImageUrl(mData.get(position).status.original_pic);
        holder.imageView.loadImage();
    }
    return convertView;
    */
    @Override
    public abstract View getView(int position, View convertView, ViewGroup parent);

}
