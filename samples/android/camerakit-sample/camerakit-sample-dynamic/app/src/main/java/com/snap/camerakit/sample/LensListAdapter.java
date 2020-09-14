package com.snap.camerakit.sample;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.snap.camerakit.lenses.LensesComponent;
import com.snap.camerakit.sample.dynamic.app.R;

import java.util.List;

final class LensListAdapter extends RecyclerView.Adapter<LensListAdapter.ViewHolder> {

    private final List<LensesComponent.Lens> lenses;

    LensListAdapter(List<LensesComponent.Lens> lenses) {
        this.lenses = lenses;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.lens_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(lenses.get(position));
    }

    @Override
    public int getItemCount() {
        return lenses.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView lensIdView;
        private final ImageView lensIconView;
        private final TextView lensNameView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            lensIdView = itemView.findViewById(R.id.lens_id);
            lensIconView = itemView.findViewById(R.id.lens_icon);
            lensNameView = itemView.findViewById(R.id.lens_name);
        }

        public void bind(LensesComponent.Lens lens) {
            lensIdView.setText(lens.getId());
            lensNameView.setText(lens.getName());
            Glide.with(itemView).load(lens.getIconUri()).into(lensIconView);
        }
    }
}
