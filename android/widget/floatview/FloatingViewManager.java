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

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.DisplayCutout;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * 一个关于Floating View的类。。
 * TODO:动作各不相同，找原因
 * TODO:追踪移动的复数显示支持在第2弹中支持
 */
public class FloatingViewManager implements ScreenChangedListener, View.OnTouchListener, TrashViewListener {

    /**
     * 经常显示的模式
     */
    public static final int DISPLAY_MODE_SHOW_ALWAYS = 1;

    /**
     * 经常隐藏的模式
     */
    public static final int DISPLAY_MODE_HIDE_ALWAYS = 2;

    /**
     * 全面屏幕时不显示的模式
     */
    public static final int DISPLAY_MODE_HIDE_FULLSCREEN = 3;

    /**
     * 显示模式
     */
    @IntDef({DISPLAY_MODE_SHOW_ALWAYS, DISPLAY_MODE_HIDE_ALWAYS, DISPLAY_MODE_HIDE_FULLSCREEN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DisplayMode {
    }

    /**
     * 向左右相近的方向移动
     */
    public static final int MOVE_DIRECTION_DEFAULT = 0;
    /**
     * 一直向左移动
     */
    public static final int MOVE_DIRECTION_LEFT = 1;
    /**
     * 一直向右移动
     */
    public static final int MOVE_DIRECTION_RIGHT = 2;

    /**
     * 不移动
     */
    public static final int MOVE_DIRECTION_NONE = 3;

    /**
     * 向靠近的方向移动。
     */
    public static final int MOVE_DIRECTION_NEAREST = 4;

    /**
     * Goes in the direction in which it is thrown
     */
    public static final int MOVE_DIRECTION_THROWN = 5;

    /**
     * Moving direction
     */
    @IntDef({MOVE_DIRECTION_DEFAULT, MOVE_DIRECTION_LEFT, MOVE_DIRECTION_RIGHT,
            MOVE_DIRECTION_NEAREST, MOVE_DIRECTION_NONE, MOVE_DIRECTION_THROWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MoveDirection {
    }

    /**
     * 如果View的形状是圆形
     */
    public static final float SHAPE_CIRCLE = 1.0f;

    /**
     * 如果View的形状是四边形
     */
    public static final float SHAPE_RECTANGLE = 1.4142f;

    /**
     * {@link Context}
     */
    private final Context mContext;

    /**
     * {@link Resources}
     */
    private final Resources mResources;

    /**
     * WindowManager
     */
    private final WindowManager mWindowManager;

    /**
     * {@link DisplayMetrics}
     */
    private final DisplayMetrics mDisplayMetrics;

    /**
     * 操作状态的Floating View
     */
    private FloatingView mTargetFloatingView;

    /**
     * 监视整个屏幕的视图。
     */
    private final FullscreenObserverView mFullscreenObserverView;

    /**
     * 删除Floating View的View。
     */
    private final TrashView mTrashView;

    /**
     * FloatingViewListener
     */
    private final FloatingViewListener mFloatingViewListener;

    /**
     * 每个Floating View的判断矩形
     */
    private final Rect mFloatingViewRect;

    /**
     * 每个Trash View的判断矩形
     */
    private final Rect mTrashViewRect;

    /**
     * 允许触摸移动的标志
     * 这是防止屏幕旋转时触摸处理的标志。
     */
    private boolean mIsMoveAccept;

    /**
     * 当前的显示模式
     */
    @DisplayMode
    private int mDisplayMode;

    /**
     * Cutout safe inset rect
     */
    private final Rect mSafeInsetRect;

    /**
     * 粘贴在Window的Floating View列表
     * TODO:第2弹的Floating View的复数表示发挥意义的预定
     */
    private final ArrayList<FloatingView> mFloatingViewList;

    /**
     * 构造器
     *
     * @param context  Context
     * @param listener FloatingViewListener
     */
    public FloatingViewManager(Context context, FloatingViewListener listener) {
        mContext = context;
        mResources = context.getResources();
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplayMetrics = new DisplayMetrics();
        mFloatingViewListener = listener;
        mFloatingViewRect = new Rect();
        mTrashViewRect = new Rect();
        mIsMoveAccept = false;
        mDisplayMode = DISPLAY_MODE_HIDE_FULLSCREEN;
        mSafeInsetRect = new Rect();

        // 构建与Floating View合作的View
        mFloatingViewList = new ArrayList<>();
        mFullscreenObserverView = new FullscreenObserverView(context, this);
        mTrashView = new TrashView(context);
    }

    /**
     * 检查是否与删除视图重叠。。
     *
     * @return 如果与删除View重叠，则为true
     */
    private boolean isIntersectWithTrash() {
        // 无效的情况下不进行重叠判定
        if (!mTrashView.isTrashEnabled()) {
            return false;
        }
        // INFO:Trash View和Floating View需要相同的Gravity。
        mTrashView.getWindowDrawingRect(mTrashViewRect);
        mTargetFloatingView.getWindowDrawingRect(mFloatingViewRect);
        return Rect.intersects(mTrashViewRect, mFloatingViewRect);
    }

    /**
     * 如果屏幕是全屏的，就隐藏View。
     */
    @Override
    public void onScreenChanged(Rect windowRect, int visibility) {
        // detect status bar
        final boolean isFitSystemWindowTop = windowRect.top == 0;
        boolean isHideStatusBar;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 && visibility != FullscreenObserverView.NO_LAST_VISIBILITY) {
            // Support for screen rotation when setSystemUiVisibility is used
            isHideStatusBar = isFitSystemWindowTop || (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == View.SYSTEM_UI_FLAG_LOW_PROFILE;
        } else {
            isHideStatusBar = isFitSystemWindowTop;
        }

        // detect navigation bar
        final boolean isHideNavigationBar;
        if (visibility == FullscreenObserverView.NO_LAST_VISIBILITY) {
            // At the first it can not get the correct value, so do special processing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mWindowManager.getDefaultDisplay().getRealMetrics(mDisplayMetrics);
                isHideNavigationBar = windowRect.width() - mDisplayMetrics.widthPixels == 0 && windowRect.bottom - mDisplayMetrics.heightPixels == 0;
            } else {
                mWindowManager.getDefaultDisplay().getMetrics(mDisplayMetrics);
                isHideNavigationBar = windowRect.width() - mDisplayMetrics.widthPixels > 0 || windowRect.height() - mDisplayMetrics.heightPixels > 0;
            }
        } else {
            isHideNavigationBar = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        final boolean isPortrait = mResources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        // update FloatingView layout
        mTargetFloatingView.onUpdateSystemLayout(isHideStatusBar, isHideNavigationBar, isPortrait, windowRect);

        // 不是全面屏幕的非显示模式的情况下什么都不做
        if (mDisplayMode != DISPLAY_MODE_HIDE_FULLSCREEN) {
            return;
        }

        mIsMoveAccept = false;
        final int state = mTargetFloatingView.getState();
        // 不重叠的情况下全部隐藏处理
        if (state == FloatingView.STATE_NORMAL) {
            final int size = mFloatingViewList.size();
            for (int i = 0; i < size; i++) {
                final FloatingView floatingView = mFloatingViewList.get(i);
                floatingView.setVisibility(isFitSystemWindowTop ? View.GONE : View.VISIBLE);
            }
            mTrashView.dismiss();
        }
        // 重叠时删除
        else if (state == FloatingView.STATE_INTERSECTING) {
            mTargetFloatingView.setFinishing();
            mTrashView.dismiss();
        }
    }

    /**
     * Update ActionTrashIcon
     */
    @Override
    public void onUpdateActionTrashIcon() {
        mTrashView.updateActionTrashIcon(mTargetFloatingView.getMeasuredWidth(), mTargetFloatingView.getMeasuredHeight(), mTargetFloatingView.getShape());
    }

    /**
     * 锁定FloatingView的触摸。
     */
    @Override
    public void onTrashAnimationStarted(@TrashView.AnimationState int animationCode) {
        // 关闭或强制关闭时，不让触摸所有FloatingView
        if (animationCode == TrashView.ANIMATION_CLOSE || animationCode == TrashView.ANIMATION_FORCE_CLOSE) {
            final int size = mFloatingViewList.size();
            for (int i = 0; i < size; i++) {
                final FloatingView floatingView = mFloatingViewList.get(i);
                floatingView.setDraggable(false);
            }
        }
    }

    /**
     * FloatingView的触控解锁。。
     */
    @Override
    public void onTrashAnimationEnd(@TrashView.AnimationState int animationCode) {

        final int state = mTargetFloatingView.getState();
        // 終了していたらViewを削除する
        if (state == FloatingView.STATE_FINISHING) {
            removeViewToWindow(mTargetFloatingView);
        }

        // すべてのFloatingViewのタッチ状態を戻す
        final int size = mFloatingViewList.size();
        for (int i = 0; i < size; i++) {
            final FloatingView floatingView = mFloatingViewList.get(i);
            floatingView.setDraggable(true);
        }

    }

    /**
     * 处理显示或隐藏删除按钮。
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int action = event.getAction();

        // 如果不是按下状态却没有移动许可，则什么也不做(对应于旋转后马上来ACTION MOVE, Floating View消失的现象)
        if (action != MotionEvent.ACTION_DOWN && !mIsMoveAccept) {
            return false;
        }

        final int state = mTargetFloatingView.getState();
        mTargetFloatingView = (FloatingView) v;

        // 按下
        if (action == MotionEvent.ACTION_DOWN) {
            mIsMoveAccept = true;
        }
        // 移动
        else if (action == MotionEvent.ACTION_MOVE) {
            // 这次的状态
            final boolean isIntersecting = isIntersectWithTrash();
            // 到目前为止的状态
            final boolean isIntersect = state == FloatingView.STATE_INTERSECTING;
            // 重叠的情况下，让Floating View跟随Trash View
            if (isIntersecting) {
                mTargetFloatingView.setIntersecting((int) mTrashView.getTrashIconCenterX(), (int) mTrashView.getTrashIconCenterY());
            }
            // 开始重叠的情况
            if (isIntersecting && !isIntersect) {
                mTargetFloatingView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                mTrashView.setScaleTrashIcon(true);
            }
            // 重叠结束的情况
            else if (!isIntersecting && isIntersect) {
                mTargetFloatingView.setNormal();
                mTrashView.setScaleTrashIcon(false);
            }

        }
        // 按上，取消
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 重叠的情况
            if (state == FloatingView.STATE_INTERSECTING) {
                // 删除FloatingView，解除放大状态
                mTargetFloatingView.setFinishing();
                mTrashView.setScaleTrashIcon(false);
            }
            mIsMoveAccept = false;

            // Touch finish callback
            if (mFloatingViewListener != null) {
                final boolean isFinishing = mTargetFloatingView.getState() == FloatingView.STATE_FINISHING;
                final WindowManager.LayoutParams params = mTargetFloatingView.getWindowLayoutParams();
                mFloatingViewListener.onTouchFinished(isFinishing, params.x, params.y);
            }
        }

        // 向TrashView通知事件
        // 在正常情况下，传递手指的位置
        // 如果重叠，就传递TrashView的位置
        if (state == FloatingView.STATE_INTERSECTING) {
            mTrashView.onTouchFloatingView(event, mFloatingViewRect.left, mFloatingViewRect.top);
        } else {
            final WindowManager.LayoutParams params = mTargetFloatingView.getWindowLayoutParams();
            mTrashView.onTouchFloatingView(event, params.x, params.y);
        }

        return false;
    }

    /**
     * 设置固定删除图标的图像。
     *
     * @param resId drawable ID
     */
    public void setFixedTrashIconImage(@DrawableRes int resId) {
        mTrashView.setFixedTrashIconImage(resId);
    }

    /**
     * 设置删除图标的图像。
     *
     * @param resId drawable ID
     */
    public void setActionTrashIconImage(@DrawableRes int resId) {
        mTrashView.setActionTrashIconImage(resId);
    }

    /**
     * 设置固定删除图标。
     *
     * @param drawable Drawable
     */
    public void setFixedTrashIconImage(Drawable drawable) {
        mTrashView.setFixedTrashIconImage(drawable);
    }

    /**
     * 设置动作删除图标。
     *
     * @param drawable Drawable
     */
    public void setActionTrashIconImage(Drawable drawable) {
        mTrashView.setActionTrashIconImage(drawable);
    }

    /**
     * 更改显示模式。
     *
     * @param displayMode {@link #DISPLAY_MODE_SHOW_ALWAYS} or {@link #DISPLAY_MODE_HIDE_ALWAYS} or {@link #DISPLAY_MODE_HIDE_FULLSCREEN}
     */
    public void setDisplayMode(@DisplayMode int displayMode) {
        mDisplayMode = displayMode;
        // 总是显示全屏幕时非显示模式的情况
        if (mDisplayMode == DISPLAY_MODE_SHOW_ALWAYS || mDisplayMode == DISPLAY_MODE_HIDE_FULLSCREEN) {
            for (FloatingView floatingView : mFloatingViewList) {
                floatingView.setVisibility(View.VISIBLE);
            }
        }
        // 总是隐藏模式的情况
        else if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            for (FloatingView floatingView : mFloatingViewList) {
                floatingView.setVisibility(View.GONE);
            }
            mTrashView.dismiss();
        }
    }

    /**
     * 设置Trash View的显示或隐藏。
     *
     * @param enabled true时表示
     */
    public void setTrashViewEnabled(boolean enabled) {
        mTrashView.setTrashEnabled(enabled);
    }

    /**
     * 获取TrashView的显示隐藏状态。
     *
     * @return 为true时为显示状态(重叠判定有效的状态)
     */
    public boolean isTrashViewEnabled() {
        return mTrashView.isTrashEnabled();
    }

    /**
     * Set the DisplayCutout's safe area
     * Note:You must set the Cutout obtained on portrait orientation.
     *
     * @param safeInsetRect DisplayCutout#getSafeInsetXXX
     */
    public void setSafeInsetRect(Rect safeInsetRect) {
        if (safeInsetRect == null) {
            mSafeInsetRect.setEmpty();
        } else {
            mSafeInsetRect.set(safeInsetRect);
        }

        final int size = mFloatingViewList.size();
        if (size == 0) {
            return;
        }

        // update floating view
        for (int i = 0; i < size; i++) {
            final FloatingView floatingView = mFloatingViewList.get(i);
            floatingView.setSafeInsetRect(mSafeInsetRect);
        }
        // dirty hack
        mFullscreenObserverView.onGlobalLayout();
    }

    /**
     * 将View粘贴到window中。
     *
     * @param view    使之浮动的View
     * @param options Options
     */
    public void addViewToWindow(View view, Options options) {
        final boolean isFirstAttach = mFloatingViewList.isEmpty();
        // FloatingView
        final FloatingView floatingView = new FloatingView(mContext);
        floatingView.setInitCoords(options.floatingViewX, options.floatingViewY);
        floatingView.setOnTouchListener(this);
        floatingView.setShape(options.shape);
        floatingView.setOverMargin(options.overMargin);
        floatingView.setMoveDirection(options.moveDirection);
        floatingView.usePhysics(options.usePhysics);
        floatingView.setAnimateInitialMove(options.animateInitialMove);
        floatingView.setSafeInsetRect(mSafeInsetRect);

        // set FloatingView size
        final FrameLayout.LayoutParams targetParams = new FrameLayout.LayoutParams(options.floatingViewWidth, options.floatingViewHeight);
        view.setLayoutParams(targetParams);
        floatingView.addView(view);

        // 非显示模式的情况
        if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            floatingView.setVisibility(View.GONE);
        }
        mFloatingViewList.add(floatingView);
        // TrashView
        mTrashView.setTrashViewListener(this);

        // View的粘贴
        mWindowManager.addView(floatingView, floatingView.getWindowLayoutParams());
        // 仅在第一次粘贴时粘贴全屏监视视图和删除视图
        if (isFirstAttach) {
            mWindowManager.addView(mFullscreenObserverView, mFullscreenObserverView.getWindowLayoutParams());
            mTargetFloatingView = floatingView;
        } else {
            removeViewImmediate(mTrashView);
        }
        // 因为我希望它一定会出现在顶部，所以我每次都粘贴它。
        mWindowManager.addView(mTrashView, mTrashView.getWindowLayoutParams());
    }

    /**
     * 从window中移除View。
     *
     * @param floatingView FloatingView
     */
    private void removeViewToWindow(FloatingView floatingView) {
        final int matchIndex = mFloatingViewList.indexOf(floatingView);
        // 如果被发现，就显示并从列表中删除
        if (matchIndex != -1) {
            removeViewImmediate(floatingView);
            mFloatingViewList.remove(matchIndex);
        }

        // 检查剩下的View
        if (mFloatingViewList.isEmpty()) {
            // 通知结束
            if (mFloatingViewListener != null) {
                mFloatingViewListener.onFinishFloatingView();
            }
        }
    }

    /**
     * 把View从window中全部取下。
     */
    public void removeAllViewToWindow() {
        removeViewImmediate(mFullscreenObserverView);
        removeViewImmediate(mTrashView);
        //删除FloatingView
        final int size = mFloatingViewList.size();
        for (int i = 0; i < size; i++) {
            final FloatingView floatingView = mFloatingViewList.get(i);
            removeViewImmediate(floatingView);
        }
        mFloatingViewList.clear();
    }

    /**
     * Safely remove the View (issue #89)
     *
     * @param view {@link View}
     */
    private void removeViewImmediate(View view) {
        // fix #100(crashes on Android 8)
        try {
            mWindowManager.removeViewImmediate(view);
        } catch (IllegalArgumentException e) {
            //do nothing
        }
    }

    /**
     * Find the safe area of DisplayCutout.
     *
     * @param activity {@link Activity} (Portrait and `windowLayoutInDisplayCutoutMode` != never)
     * @return Safe cutout insets.
     */
    public static Rect findCutoutSafeArea(@NonNull Activity activity) {
        final Rect safeInsetRect = new Rect();
        // TODO:Rewrite with android-x
        // TODO:Consider alternatives
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return safeInsetRect;
        }

        // Fix: getDisplayCutout() on a null object reference (issue #110)
        final WindowInsets windowInsets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (windowInsets == null) {
            return safeInsetRect;
        }

        // set safeInsetRect
        final DisplayCutout displayCutout = windowInsets.getDisplayCutout();
        if (displayCutout != null) {
            safeInsetRect.set(displayCutout.getSafeInsetLeft(), displayCutout.getSafeInsetTop(), displayCutout.getSafeInsetRight(), displayCutout.getSafeInsetBottom());
        }

        return safeInsetRect;
    }

    /**
     * 表示粘贴Floating View时的选项的类。
     */
    public static class Options {

        /**
         * 浮动的矩形视图(shape_rectangle or shape_circle)
         */
        public float shape;

        /**
         * 画面外的外溢差额(px)
         */
        public int overMargin;

        /**
         * 以画面左下为原点的FloatingView的X坐标
         */
        public int floatingViewX;

        /**
         * 以画面左下为原点的FloatingView的Y坐标
         */
        public int floatingViewY;

        /**
         * Width of FloatingView(px)
         */
        public int floatingViewWidth;

        /**
         * Height of FloatingView(px)
         */
        public int floatingViewHeight;

        /**
         * FloatingView吸附的方向
         * ※指定坐标后自动变成MOVE DIRECTION NONE
         */
        @MoveDirection
        public int moveDirection;

        /**
         * Use of physics-based animations or (default) ValueAnimation
         */
        public boolean usePhysics;

        /**
         * 初始显示时动画的标志
         */
        public boolean animateInitialMove;

        /**
         * 设定选项的默认值。
         */
        public Options() {
            shape = SHAPE_CIRCLE;
            overMargin = 0;
            floatingViewX = FloatingView.DEFAULT_X;
            floatingViewY = FloatingView.DEFAULT_Y;
            floatingViewWidth = FloatingView.DEFAULT_WIDTH;
            floatingViewHeight = FloatingView.DEFAULT_HEIGHT;
            moveDirection = MOVE_DIRECTION_DEFAULT;
            usePhysics = true;
            animateInitialMove = true;
        }

    }

}
