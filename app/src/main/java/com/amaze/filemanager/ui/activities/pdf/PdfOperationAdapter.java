/*
 * Copyright (C) 2014-2026 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.ui.activities.pdf;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.amaze.filemanager.R;

import java.util.List;

/**
 * Adapter for PDF operation list
 */
public class PdfOperationAdapter extends BaseAdapter {

    private Context context;
    private List<PdfEditorActivity.PdfOperation> operations;

    public PdfOperationAdapter(Context context, List<PdfEditorActivity.PdfOperation> operations) {
        this.context = context;
        this.operations = operations;
    }

    @Override
    public int getCount() {
        return operations.size();
    }

    @Override
    public Object getItem(int position) {
        return operations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_pdf_operation, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.iv_operation_icon);
            holder.title = convertView.findViewById(R.id.tv_operation_title);
            holder.description = convertView.findViewById(R.id.tv_operation_description);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        PdfEditorActivity.PdfOperation operation = operations.get(position);
        holder.icon.setImageResource(operation.getIconResId());
        holder.title.setText(operation.getTitle());
        holder.description.setText(operation.getDescription());

        return convertView;
    }

    static class ViewHolder {
        ImageView icon;
        TextView title;
        TextView description;
    }
}
