/**
 * Copyright 2015 RECRUIT LIFESTYLE CO., LTD.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taptap.common.widget.floatview;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

/**
 * 是监视全面屏幕的View。
 * http://stackoverflow.com/questions/18551135/receiving-hidden-status-bar-entering-a-full-screen-activity-event-on-a-service/19201933#19201933
 */
class FullscreenObserverView extends View implements ViewTreeObserver.OnGlobalLayoutListener, View.OnSystemUiVisibilityChangeListener {

    /**
     * Constant that mLastUiVisibility does not exist.
     */
    static final int NO_LAST_VISIBILITY = -1;

    /**
     * Overlay Type
     */
    private static final int OVERLAY_TYPE;

    /**
     * WindowManager.LayoutParams
     */
    private final WindowManager.LayoutParams mParams;

    /**
     * ScreenListener
     */
    private final ScreenChangedListener mScreenChangedListener;

    /**
     * 最后的显示状态(on System Ui Visibility Change有时不会来，自己保持)
     * ※不来时:Immersive Mode→触摸状态栏→状态栏消失
     */
    private int mLastUiVisibility;

    /**
     * Window的Rect
     */
    private final Rect mWindowRect;

    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        } else {
            OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
    }

    /**
     * 构造器
     */
    FullscreenObserverView(Context context, ScreenChangedListener listener) {
        super(context);

        // Listener的组合
        mScreenChangedListener = listener;

        // 准备宽1，高最大的透明视图，检测布局的变化
        mParams = new WindowManager.LayoutParams();
        mParams.width = 1;
        mParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mParams.type = OVERLAY_TYPE;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mParams.format = PixelFormat.TRANSLUCENT;

        mWindowRect = new Rect();
        mLastUiVisibility = NO_LAST_VISIBILITY;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
        setOnSystemUiVisibilityChangeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        // 删除版面变化通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getViewTreeObserver().removeOnGlobalLayoutListener(this);
        } else {
            //noinspection弃用
            getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
        setOnSystemUiVisibilityChangeListener(null);
        super.onDetachedFromWindow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onGlobalLayout() {
        // 获取View(全屏)的大小
        if (mScreenChangedListener != null) {
            getWindowVisibleDisplayFrame(mWindowRect);
            mScreenChangedListener.onScreenChanged(mWindowRect, mLastUiVisibility);
        }
    }

    /**
     * 在导航栏进行处理的应用程序(onGlobalLayout事件不发生的情况下)中使用。
     * (例如Nexus 5的相机应用)
     */
    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        mLastUiVisibility = visibility;
        // 受导航条的变化显示·非显示切换
        if (mScreenChangedListener != null) {
            getWindowVisibleDisplayFrame(mWindowRect);
            mScreenChangedListener.onScreenChanged(mWindowRect, visibility);
        }
    }

    /**
     * WindowManager.LayoutParams
     *
     * @return WindowManager.LayoutParams
     */
    WindowManager.LayoutParams getWindowLayoutParams() {
        return mParams;
    }
}