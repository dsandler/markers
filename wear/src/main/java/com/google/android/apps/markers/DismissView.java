package com.google.android.apps.markers;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import org.dsandler.apps.markers.R;

/**
 * Adapted from DismissOverlayView.
 */
public class DismissView extends FrameLayout {
    private View mDismissButton;

    public DismissView(Context context) {
        this(context, null, 0);
    }

    public DismissView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DismissView(final Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater.from(context).inflate(R.layout.dismiss_overlay, this, true);
        setBackgroundResource(R.color.dismiss_overlay_bg);
        setClickable(true);

        mDismissButton = findViewById(R.id.dismiss_overlay_button);
        mDismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof Activity) {
                    ((Activity) context).finish();
                }
            }
        });

        setVisibility(View.GONE);
    }

    /**
     * Show the exit button.
     *
     * This should be called from a long-press listener.
     */
    public void show() {
        setAlpha(0f);
        mDismissButton.setVisibility(View.VISIBLE);
        setVisibility(View.VISIBLE);
        animate().alpha(1f).setDuration(200).start();
    }

    private void hide() {
        animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                setVisibility(View.GONE);
                setAlpha(1f);
            }
        }).start();
    }

    @Override
    public boolean performClick() {
        hide();
        return true;
    }

}
