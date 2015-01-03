package com.google.android.apps.markers;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import android.widget.LinearLayout;
import org.dsandler.apps.markers.R;

/**
 * Adapted from DismissOverlayView.
 */
public class HudView extends FrameLayout {
    private View mDismissButton, mShareButton;
    private Activity mActivity;
    private MarkersWearActivity.MicroSlateView mSlate;
    private ToolButton.PenToolButton mPenTool;
    private PenWidthEditorView mPenWidthEditor;

    public HudView(Context context) {
        this(context, null, 0);
    }

    public HudView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HudView(final Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater.from(context).inflate(R.layout.hud, this, true);
        setBackgroundColor(getResources().getColor(R.color.hud_bg));
        setClickable(true);

        mDismissButton = findViewById(R.id.dismiss_button);
        mDismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.finish();
            }
        });

        mShareButton = findViewById(R.id.share_button);
        mShareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSlate.doShare();
                hide();
            }
        });

        // HUD setup
        LinearLayout colors1 = (LinearLayout) findViewById(R.id.colors1);
        LinearLayout colors2 = (LinearLayout) findViewById(R.id.colors2);

        final int[] COLORS1 = {
                0,          0xFF000000, 0xFF404040, 0xFF808080, 0xFFCCCCCC, 0xFFFFFFFF,
        };
        final int[] COLORS2 = {
                0xFFFF0000, 0xFFFF8000, 0xFFFFFF00, 0xFF00FF00, 0xFF0000FF, 0xFF8000FF
        };
        for (int i=0; i<COLORS1.length; i++) {
            final ToolButton.SwatchButton swatch = new ToolButton.SwatchButton(context, null);
            swatch.setColor(COLORS1[i]);
            swatch.setCallback(mToolCallback);
            colors1.addView(swatch, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
            ));
        }
        for (int i=0; i<COLORS2.length; i++) {
            final ToolButton.SwatchButton swatch = new ToolButton.SwatchButton(context, null);
            swatch.setColor(COLORS2[i]);
            swatch.setCallback(mToolCallback);
            colors2.addView(swatch, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
            ));
        }

        mPenTool = (ToolButton.PenToolButton) findViewById(R.id.pen);
        if (mSlate != null) {
            mPenTool.setWidths(mSlate.getPenMin(), mSlate.getPenMax());
        }
        mPenWidthEditor = (PenWidthEditorView) findViewById(R.id.pen_width);
        mPenWidthEditor.setTool(mPenTool);
        mPenWidthEditor.setAllowedSizes(1, 50);
        mPenTool.setCallback(mToolCallback);

        setVisibility(View.GONE);
    }

    ToolButton.ToolCallback mToolCallback = new ToolButton.ToolCallback() {
        //        public void setZoomMode(ToolButton me) {}
        public void setPenMode(ToolButton me, float min, float max) {
            if (mSlate != null) {
                mSlate.setPenMin(min);
                mSlate.setPenMax(max);
            }
        }
        public void setPenColor(ToolButton me, int color) {
            if (mSlate != null) {
                mSlate.setPenColor(color);
            }
            hide();
        }
//        public void setBackgroundColor(ToolButton me, int color) {}
//        public void restore(ToolButton me) {}
//        public void setPenType(ToolButton penTypeButton, int penType) {}
//        public void resetZoom(ToolButton zoomToolButton) { }
    };


    public void setSlate(MarkersWearActivity.MicroSlateView slate) {
        mSlate = slate;

        mPenTool.setWidths(mSlate.getPenMin(), mSlate.getPenMax());
        mPenWidthEditor.setTool(mPenTool); // update sizes
    }

    public void setActivity(Activity activity) {
        mActivity = activity;
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

    public void hide() {
        mPenTool.activate(); // apply width changes
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
