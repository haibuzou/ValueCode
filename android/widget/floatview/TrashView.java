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

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * 用来消除FloatingView的View。
 */
class TrashView extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {

    /**
     * 背景高度(dp)
     */
    private static final int BACKGROUND_HEIGHT = 164;

    /**
     * 捕获目标的水平区域(dp)
     */
    private static final float TARGET_CAPTURE_HORIZONTAL_REGION = 30.0f;

    /**
     * 捕获目标的垂直区域(dp)
     */
    private static final float TARGET_CAPTURE_VERTICAL_REGION = 4.0f;

    /**
     * 删除图标的放大/缩小动画时间
     */
    private static final long TRASH_ICON_SCALE_DURATION_MILLIS = 200L;

    /**
     * 表示无动画状态的常数
     */
    static final int ANIMATION_NONE = 0;
    /**
     * 表示显示背景·删除图标等的动画的常数<br/>
     * 也包含FloatingView的跟踪。
     */
    static final int ANIMATION_OPEN = 1;
    /**
     * 表示消除背景·删除图标等的动画的常数
     */
    static final int ANIMATION_CLOSE = 2;
    /**
     * 表示立即消除背景、删除图标等的常数
     */
    static final int ANIMATION_FORCE_CLOSE = 3;

    /**
     * Animation State
     */
    @IntDef({ANIMATION_NONE, ANIMATION_OPEN, ANIMATION_CLOSE, ANIMATION_FORCE_CLOSE})
    @Retention(RetentionPolicy.SOURCE)
    @interface AnimationState {
    }

    /**
     * 长按判定的时间
     */
    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    /**
     * Overlay Type
     */
    private static final int OVERLAY_TYPE;

    /**
     * WindowManager
     */
    private final WindowManager mWindowManager;

    /**
     * LayoutParams
     */
    private final WindowManager.LayoutParams mParams;

    /**
     * DisplayMetrics
     */
    private final DisplayMetrics mMetrics;

    /**
     * rootView(包含背景、删除图标的View)
     */
    private final ViewGroup mRootView;

    /**
     * 删除图标
     */
    private final FrameLayout mTrashIconRootView;

    /**
     * 固定的删除图标
     */
    private final ImageView mFixedTrashIconView;

    /**
     * 根据重叠动作的删除图标
     */
    private final ImageView mActionTrashIconView;

    /**
     * ActionTrashIcon的宽度
     */
    private int mActionTrashIconBaseWidth;

    /**
     * ActionTrashIcon的高度
     */
    private int mActionTrashIconBaseHeight;

    /**
     * ActionTrashIcon的最大放大率
     */
    private float mActionTrashIconMaxScale;

    /**
     * 背景View
     */
    private final FrameLayout mBackgroundView;

    /**
     * 进入删除图标框内时的动画(放大)
     */
    private ObjectAnimator mEnterScaleAnimator;

    /**
     * 跳出删除图标框时的动画效果(缩小)
     */
    private ObjectAnimator mExitScaleAnimator;

    /**
     * 进行动画的处理程序
     */
    private final AnimationHandler mAnimationHandler;

    /**
     * TrashViewListener
     */
    private TrashViewListener mTrashViewListener;

    /**
     * View的有效/无效标志(在无效的情况下不显示)
     */
    private boolean mIsEnabled;

    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;
        } else {
            OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
    }

    /**
     * 构造器
     *
     * @param context Context
     */
    TrashView(Context context) {
        super(context);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(mMetrics);
        mAnimationHandler = new AnimationHandler(this);
        mIsEnabled = true;

        mParams = new WindowManager.LayoutParams();
        mParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        mParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mParams.type = OVERLAY_TYPE;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        mParams.format = PixelFormat.TRANSLUCENT;
        // INFO:窗的原点设定在左下
        mParams.gravity = Gravity.LEFT | Gravity.BOTTOM;

        // 各种视图的设置
        // 直接粘贴到TrashView的View(如果不通过该View，删除View和背景View的布局不知为何会破坏)
        mRootView = new FrameLayout(context);
        mRootView.setClipChildren(false);
        // 删除图标的路由视图
        mTrashIconRootView = new FrameLayout(context);
        mTrashIconRootView.setClipChildren(false);
        mFixedTrashIconView = new ImageView(context);
        mActionTrashIconView = new ImageView(context);
        // 背景View
        mBackgroundView = new FrameLayout(context);
        mBackgroundView.setAlpha(0.0f);
        final GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x50000000});
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            //noinspection deprecation
            mBackgroundView.setBackgroundDrawable(gradientDrawable);
        } else {
            mBackgroundView.setBackground(gradientDrawable);
        }

        // 背景视图的粘贴
        final LayoutParams backgroundParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (BACKGROUND_HEIGHT * mMetrics.density));
        backgroundParams.gravity = Gravity.BOTTOM;
        mRootView.addView(mBackgroundView, backgroundParams);
        // 动作图标的粘贴
        final LayoutParams actionTrashIconParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionTrashIconParams.gravity = Gravity.CENTER;
        mTrashIconRootView.addView(mActionTrashIconView, actionTrashIconParams);
        // 贴有固定图标
        final LayoutParams fixedTrashIconParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fixedTrashIconParams.gravity = Gravity.CENTER;
        mTrashIconRootView.addView(mFixedTrashIconView, fixedTrashIconParams);
        // 添加删除图标
        final LayoutParams trashIconParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trashIconParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        mRootView.addView(mTrashIconRootView, trashIconParams);

        // 粘贴到TrashView
        addView(mRootView);

        // 初次绘图处理用
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    /**
     * 决定显示位置。
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateViewLayout();
    }

    /**
     * 在画面旋转时调整布局。
     */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateViewLayout();
    }

    /**
     * 进行初次绘图时的坐标设定。<br/>
     * 因为初次表示的时候有一瞬间删除图标被显示的事象。
     */
    @Override
    public boolean onPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        mTrashIconRootView.setTranslationY(mTrashIconRootView.getMeasuredHeight());
        return true;
    }

    /**
     * initialize ActionTrashIcon
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTrashViewListener.onUpdateActionTrashIcon();
    }

    /**
     * 从画面尺寸决定自己的位置。
     */
    private void updateViewLayout() {
        mWindowManager.getDefaultDisplay().getMetrics(mMetrics);
        mParams.x = (mMetrics.widthPixels - getWidth()) / 2;
        mParams.y = 0;

        // Update view and layout
        mTrashViewListener.onUpdateActionTrashIcon();
        mAnimationHandler.onUpdateViewLayout();

        mWindowManager.updateViewLayout(this, mParams);
    }

    /**
     * 隐藏TrashView。
     */
    void dismiss() {
        // 停止动画
        mAnimationHandler.removeMessages(ANIMATION_OPEN);
        mAnimationHandler.removeMessages(ANIMATION_CLOSE);
        mAnimationHandler.sendAnimationMessage(ANIMATION_FORCE_CLOSE);
        // 停止放大动画
        setScaleTrashIconImmediately(false);
    }

    /**
     * 取得在window上的绘图区域。
     * 表示每个判定的矩形。
     *
     * @param outRect 进行变更的Rect
     */
    void getWindowDrawingRect(Rect outRect) {
        // 由于Gravity是反方向的，所以矩形的判定也是上下颠倒(top/bottom)
        // 多设定一些top(画面上向下)的判断
        final ImageView iconView = hasActionTrashIcon() ? mActionTrashIconView : mFixedTrashIconView;
        final float iconPaddingLeft = iconView.getPaddingLeft();
        final float iconPaddingTop = iconView.getPaddingTop();
        final float iconWidth = iconView.getWidth() - iconPaddingLeft - iconView.getPaddingRight();
        final float iconHeight = iconView.getHeight() - iconPaddingTop - iconView.getPaddingBottom();
        final float x = mTrashIconRootView.getX() + iconPaddingLeft;
        final float y = mRootView.getHeight() - mTrashIconRootView.getY() - iconPaddingTop - iconHeight;
        final int left = (int) (x - TARGET_CAPTURE_HORIZONTAL_REGION * mMetrics.density);
        final int top = -mRootView.getHeight();
        final int right = (int) (x + iconWidth + TARGET_CAPTURE_HORIZONTAL_REGION * mMetrics.density);
        final int bottom = (int) (y + iconHeight + TARGET_CAPTURE_VERTICAL_REGION * mMetrics.density);
        outRect.set(left, top, right, bottom);
    }

    /**
     * 更新删除图标的设置。
     *
     * @param width  目标View的宽度
     * @param height 目标View的高度
     * @param shape  目标View的形状
     */
    void updateActionTrashIcon(float width, float height, float shape) {
        // 如果没有设置删除图标，就什么都不要做。
        if (!hasActionTrashIcon()) {
            return;
        }
        // 设定放大率
        mAnimationHandler.mTargetWidth = width;
        mAnimationHandler.mTargetHeight = height;
        final float newWidthScale = width / mActionTrashIconBaseWidth * shape;
        final float newHeightScale = height / mActionTrashIconBaseHeight * shape;
        mActionTrashIconMaxScale = Math.max(newWidthScale, newHeightScale);
        // ENTER动画制作
        mEnterScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mActionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, mActionTrashIconMaxScale), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, mActionTrashIconMaxScale));
        mEnterScaleAnimator.setInterpolator(new OvershootInterpolator());
        mEnterScaleAnimator.setDuration(TRASH_ICON_SCALE_DURATION_MILLIS);
        // Exit动画制作
        mExitScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mActionTrashIconView, PropertyValuesHolder.ofFloat(ImageView.SCALE_X, 1.0f), PropertyValuesHolder.ofFloat(ImageView.SCALE_Y, 1.0f));
        mExitScaleAnimator.setInterpolator(new OvershootInterpolator());
        mExitScaleAnimator.setDuration(TRASH_ICON_SCALE_DURATION_MILLIS);
    }

    /**
     * 获取删除图标的中心X坐标。
     *
     * @return 删除图标的中心X坐标
     */
    float getTrashIconCenterX() {
        final ImageView iconView = hasActionTrashIcon() ? mActionTrashIconView : mFixedTrashIconView;
        final float iconViewPaddingLeft = iconView.getPaddingLeft();
        final float iconWidth = iconView.getWidth() - iconViewPaddingLeft - iconView.getPaddingRight();
        final float x = mTrashIconRootView.getX() + iconViewPaddingLeft;
        return x + iconWidth / 2;
    }

    /**
     * 获得删除图标的中心Y坐标。
     *
     * @return 删除图标的中心Y坐标
     */
    float getTrashIconCenterY() {
        final ImageView iconView = hasActionTrashIcon() ? mActionTrashIconView : mFixedTrashIconView;
        final float iconViewHeight = iconView.getHeight();
        final float iconViewPaddingBottom = iconView.getPaddingBottom();
        final float iconHeight = iconViewHeight - iconView.getPaddingTop() - iconViewPaddingBottom;
        final float y = mRootView.getHeight() - mTrashIconRootView.getY() - iconViewHeight + iconViewPaddingBottom;
        return y + iconHeight / 2;
    }


    /**
     * 检查是否存在删除图标。
     *
     * @return 如果存在动作删除图标，则为true
     */
    private boolean hasActionTrashIcon() {
        return mActionTrashIconBaseWidth != 0 && mActionTrashIconBaseHeight != 0;
    }

    /**
     * 设置固定删除图标的图像。<br/>
     * 这个图像在浮动显示重叠的时候大小不会变化。
     *
     * @param resId drawable ID
     */
    void setFixedTrashIconImage(int resId) {
        mFixedTrashIconView.setImageResource(resId);
    }

    /**
     * 设置删除图标的图像。<br/>
     * 这个图像在浮动显示重叠的时候大小会变化。
     *
     * @param resId drawable ID
     */
    void setActionTrashIconImage(int resId) {
        mActionTrashIconView.setImageResource(resId);
        final Drawable drawable = mActionTrashIconView.getDrawable();
        if (drawable != null) {
            mActionTrashIconBaseWidth = drawable.getIntrinsicWidth();
            mActionTrashIconBaseHeight = drawable.getIntrinsicHeight();
        }
    }

    /**
     * 设置固定删除图标。<br/>
     * 这个图像在浮动显示重叠的时候大小不会变化。
     *
     * @param drawable Drawable
     */
    void setFixedTrashIconImage(Drawable drawable) {
        mFixedTrashIconView.setImageDrawable(drawable);
    }

    /**
     * 设置动作删除图标。<br/>
     * 这个图像在浮动显示重叠的时候大小会变化。
     *
     * @param drawable Drawable
     */
    void setActionTrashIconImage(Drawable drawable) {
        mActionTrashIconView.setImageDrawable(drawable);
        if (drawable != null) {
            mActionTrashIconBaseWidth = drawable.getIntrinsicWidth();
            mActionTrashIconBaseHeight = drawable.getIntrinsicHeight();
        }
    }

    /**
     * 立即改变删除图标的大小。
     *
     * @param isEnter 进入区域为true.否则为false
     */
    private void setScaleTrashIconImmediately(boolean isEnter) {
        cancelScaleTrashAnimation();

        mActionTrashIconView.setScaleX(isEnter ? mActionTrashIconMaxScale : 1.0f);
        mActionTrashIconView.setScaleY(isEnter ? mActionTrashIconMaxScale : 1.0f);
    }

    /**
     * 更改删除图标的大小。
     *
     * @param isEnter 进入区域为true.否则为false
     */
    void setScaleTrashIcon(boolean isEnter) {
        // 如果没有设置动作图标，就什么也不做
        if (!hasActionTrashIcon()) {
            return;
        }

        // 取消动画
        cancelScaleTrashAnimation();

        // 如果你进入了一个区域
        if (isEnter) {
            mEnterScaleAnimator.start();
        } else {
            mExitScaleAnimator.start();
        }
    }

    /**
     * 设置TrashView的有效或无效。
     *
     * @param enabled 为true时有效(显示)，为false时无效(隐藏)
     */
    void setTrashEnabled(boolean enabled) {
        // 设定相同的情况下什么都不做
        if (mIsEnabled == enabled) {
            return;
        }

        // 如果要隐藏就关闭
        mIsEnabled = enabled;
        if (!mIsEnabled) {
            dismiss();
        }
    }

    /**
     * 获取TrashView的显示状态。
     *
     * @return true时表示
     */
    boolean isTrashEnabled() {
        return mIsEnabled;
    }

    /**
     *删除图标的放大/缩小取消动画
     */
    private void cancelScaleTrashAnimation() {
        // 框内动画
        if (mEnterScaleAnimator != null && mEnterScaleAnimator.isStarted()) {
            mEnterScaleAnimator.cancel();
        }

        // 框架外动画
        if (mExitScaleAnimator != null && mExitScaleAnimator.isStarted()) {
            mExitScaleAnimator.cancel();
        }
    }

    /**
     * 设置TrashViewListener。
     *
     * @param listener TrashViewListener
     */
    void setTrashViewListener(TrashViewListener listener) {
        mTrashViewListener = listener;
    }

    /**
     * WindowManager.LayoutParams
     *
     * @return WindowManager.LayoutParams
     */
    WindowManager.LayoutParams getWindowLayoutParams() {
        return mParams;
    }

    /**
     * 进行与FloatingView相关的处理。
     *
     * @param event MotionEvent
     * @param x     Floating View的X坐标
     * @param y     Floating View的Y坐标
     */
    void onTouchFloatingView(MotionEvent event, float x, float y) {
        final int action = event.getAction();
        // 按下
        if (action == MotionEvent.ACTION_DOWN) {
            mAnimationHandler.updateTargetPosition(x, y);
            // 长按等待处理
            mAnimationHandler.removeMessages(ANIMATION_CLOSE);
            mAnimationHandler.sendAnimationMessageDelayed(ANIMATION_OPEN, LONG_PRESS_TIMEOUT);
        }
        // 移动
        else if (action == MotionEvent.ACTION_MOVE) {
            mAnimationHandler.updateTargetPosition(x, y);
            // 只在还没有开始开放动画的情况下执行
            if (!mAnimationHandler.isAnimationStarted(ANIMATION_OPEN)) {
                // 删除长按的信息
                mAnimationHandler.removeMessages(ANIMATION_OPEN);
                // 开放
                mAnimationHandler.sendAnimationMessage(ANIMATION_OPEN);
            }
        }
        // 按上，取消
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 删除长按的信息
            mAnimationHandler.removeMessages(ANIMATION_OPEN);
            mAnimationHandler.sendAnimationMessage(ANIMATION_CLOSE);
        }
    }

    /**
     * 控制动画的处理程序。
     */
    static class AnimationHandler extends Handler {

        /**
         * 刷新动画的毫秒
         */
        private static final long ANIMATION_REFRESH_TIME_MILLIS = 10L;

        /**
         * 背景动画时间
         */
        private static final long BACKGROUND_DURATION_MILLIS = 200L;

        /**
         * 删除图标的流行动画的开始延迟时间
         */
        private static final long TRASH_OPEN_START_DELAY_MILLIS = 200L;

        /**
         * 删除图标的打开动画时间
         */
        private static final long TRASH_OPEN_DURATION_MILLIS = 400L;

        /**
         * 删除图标的关闭动画时间
         */
        private static final long TRASH_CLOSE_DURATION_MILLIS = 200L;

        /**
         * Overshoot动画系数
         */
        private static final float OVERSHOOT_TENSION = 1.0f;

        /**
         * 删除图标的移动极限X轴偏移(dp)
         */
        private static final int TRASH_MOVE_LIMIT_OFFSET_X = 22;

        /**
         * 删除图标的移动极限Y轴偏移(dp)
         */
        private static final int TRASH_MOVE_LIMIT_TOP_OFFSET = -4;

        /**
         * 表示动画开始的常数
         */
        private static final int TYPE_FIRST = 1;
        /**
         * 表示动画更新的常数
         */
        private static final int TYPE_UPDATE = 2;

        /**
         * 阿尔法的最大值
         */
        private static final float MAX_ALPHA = 1.0f;

        /**
         * 阿尔法的最小值
         */
        private static final float MIN_ALPHA = 0.0f;

        /**
         * 动画开始的时间
         */
        private long mStartTime;

        /**
         * 开始动画时的alpha值
         */
        private float mStartAlpha;

        /**
         * 开始动画时的Transition Y
         */
        private float mStartTransitionY;

        /**
         * 执行中的动画代码
         */
        private int mStartedCode;

        /**
         * 追随对象的X坐标
         */
        private float mTargetPositionX;

        /**
         * 追随对象的Y坐标
         */
        private float mTargetPositionY;

        /**
         * 追随对象的范围
         */
        private float mTargetWidth;

        /**
         * 追随对象的高度
         */
        private float mTargetHeight;

        /**
         * 删除图标的移动极限位置
         */
        private final Rect mTrashIconLimitPosition;

        /**
         * Y轴的追随范围
         */
        private float mMoveStickyYRange;

        /**
         * OvershootInterpolator
         */
        private final OvershootInterpolator mOvershootInterpolator;


        /**
         * TrashView
         */
        private final WeakReference<TrashView> mTrashView;

        /**
         * 构造器
         */
        AnimationHandler(TrashView trashView) {
            mTrashView = new WeakReference<>(trashView);
            mStartedCode = ANIMATION_NONE;
            mTrashIconLimitPosition = new Rect();
            mOvershootInterpolator = new OvershootInterpolator(OVERSHOOT_TENSION);
        }

        /**
         * 进行动画的处理。
         */
        @Override
        public void handleMessage(Message msg) {
            final TrashView trashView = mTrashView.get();
            if (trashView == null) {
                removeMessages(ANIMATION_OPEN);
                removeMessages(ANIMATION_CLOSE);
                removeMessages(ANIMATION_FORCE_CLOSE);
                return;
            }

            // 如果没有效果，就不要动画
            if (!trashView.isTrashEnabled()) {
                return;
            }

            final int animationCode = msg.what;
            final int animationType = msg.arg1;
            final FrameLayout backgroundView = trashView.mBackgroundView;
            final FrameLayout trashIconRootView = trashView.mTrashIconRootView;
            final TrashViewListener listener = trashView.mTrashViewListener;
            final float screenWidth = trashView.mMetrics.widthPixels;
            final float trashViewX = trashView.mParams.x;

            // 开始动画时的初始化
            if (animationType == TYPE_FIRST) {
                mStartTime = SystemClock.uptimeMillis();
                mStartAlpha = backgroundView.getAlpha();
                mStartTransitionY = trashIconRootView.getTranslationY();
                mStartedCode = animationCode;
                if (listener != null) {
                    listener.onTrashAnimationStarted(mStartedCode);
                }
            }
            // 经过的时间
            final float elapsedTime = SystemClock.uptimeMillis() - mStartTime;

            // 显示动画
            if (animationCode == ANIMATION_OPEN) {
                final float currentAlpha = backgroundView.getAlpha();
                // 如果你没有达到最大的α值，
                if (currentAlpha < MAX_ALPHA) {
                    final float alphaTimeRate = Math.min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f);
                    final float alpha = Math.min(mStartAlpha + alphaTimeRate, MAX_ALPHA);
                    backgroundView.setAlpha(alpha);
                }

                // 如果超过DelayTime就开始动画
                if (elapsedTime >= TRASH_OPEN_START_DELAY_MILLIS) {
                    final float screenHeight = trashView.mMetrics.heightPixels;
                    // 图标左右全部凸出的话分别0%、100%的计算
                    final float positionX = trashViewX + (mTargetPositionX + mTargetWidth) / (screenWidth + mTargetWidth) * mTrashIconLimitPosition.width() + mTrashIconLimitPosition.left;
                    // 删除图标的Y坐标动画和追随(向上方向为负数)
                    // targetPositionYRate在目标的Y坐标完全离开屏幕时为0%，在屏幕的一半以后为100%
                    // stickyPositionY是移动极限的下端在原点移动到上端。mMoveStickyRange是追随的范围
                    // 通过positionY的计算，随着时间的推移移动
                    final float targetPositionYRate = Math.min(2 * (mTargetPositionY + mTargetHeight) / (screenHeight + mTargetHeight), 1.0f);
                    final float stickyPositionY = mMoveStickyYRange * targetPositionYRate + mTrashIconLimitPosition.height() - mMoveStickyYRange;
                    final float translationYTimeRate = Math.min((elapsedTime - TRASH_OPEN_START_DELAY_MILLIS) / TRASH_OPEN_DURATION_MILLIS, 1.0f);
                    final float positionY = mTrashIconLimitPosition.bottom - stickyPositionY * mOvershootInterpolator.getInterpolation(translationYTimeRate);
                    trashIconRootView.setTranslationX(positionX);
                    trashIconRootView.setTranslationY(positionY);
                    // clear drag view garbage
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        clearClippedChildren(trashView.mRootView);
                        clearClippedChildren(trashView.mTrashIconRootView);
                    }
                }

                sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
            }
            // 非显示动画
            else if (animationCode == ANIMATION_CLOSE) {
                // 阿尔法值的计算
                final float alphaElapseTimeRate = Math.min(elapsedTime / BACKGROUND_DURATION_MILLIS, 1.0f);
                final float alpha = Math.max(mStartAlpha - alphaElapseTimeRate, MIN_ALPHA);
                backgroundView.setAlpha(alpha);

                // 删除图标的Y坐标动画
                final float translationYTimeRate = Math.min(elapsedTime / TRASH_CLOSE_DURATION_MILLIS, 1.0f);
                // 如果动画没有完成，
                if (alphaElapseTimeRate < 1.0f || translationYTimeRate < 1.0f) {
                    final float position = mStartTransitionY + mTrashIconLimitPosition.height() * translationYTimeRate;
                    trashIconRootView.setTranslationY(position);
                    sendMessageAtTime(newMessage(animationCode, TYPE_UPDATE), SystemClock.uptimeMillis() + ANIMATION_REFRESH_TIME_MILLIS);
                } else {
                    // 强制调整位置
                    trashIconRootView.setTranslationY(mTrashIconLimitPosition.bottom);
                    mStartedCode = ANIMATION_NONE;
                    if (listener != null) {
                        listener.onTrashAnimationEnd(ANIMATION_CLOSE);
                    }
                }
            }
            // 即時非表示
            else if (animationCode == ANIMATION_FORCE_CLOSE) {
                backgroundView.setAlpha(0.0f);
                trashIconRootView.setTranslationY(mTrashIconLimitPosition.bottom);
                mStartedCode = ANIMATION_NONE;
                if (listener != null) {
                    listener.onTrashAnimationEnd(ANIMATION_FORCE_CLOSE);
                }
            }
        }

        /**
         * Clear the animation garbage of the target view.
         */
        private static void clearClippedChildren(ViewGroup viewGroup) {
            viewGroup.setClipChildren(true);
            viewGroup.invalidate();
            viewGroup.setClipChildren(false);
        }

        /**
         * 发送动画信息。
         *
         * @param animation   ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         * @param delayMillis 消息的发送时间
         */
        void sendAnimationMessageDelayed(int animation, long delayMillis) {
            sendMessageAtTime(newMessage(animation, TYPE_FIRST), SystemClock.uptimeMillis() + delayMillis);
        }

        /**
         * 发送动画信息。
         *
         * @param animation ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         */
        void sendAnimationMessage(int animation) {
            sendMessage(newMessage(animation, TYPE_FIRST));
        }

        /**
         * 生成要发送的信息。
         *
         * @param animation ANIMATION_OPEN,ANIMATION_CLOSE,ANIMATION_FORCE_CLOSE
         * @param type      TYPE_FIRST,TYPE_UPDATE
         * @return Message
         */
        private static Message newMessage(int animation, int type) {
            final Message message = Message.obtain();
            message.what = animation;
            message.arg1 = type;
            return message;
        }

        /**
         * 检查动画是否开始了。
         *
         * @param animationCode 动画代码
         * @return 如果动画已经开始，则为true，否则为false。
         */
        boolean isAnimationStarted(int animationCode) {
            return mStartedCode == animationCode;
        }

        /**
         * 更新追随对象的位置信息。
         *
         * @param x 追随对象的X坐标
         * @param y 追随对象的Y坐标
         */
        void updateTargetPosition(float x, float y) {
            mTargetPositionX = x;
            mTargetPositionY = y;
        }

        /**
         * View的显示状态被改变的时候会被调用。
         */
        void onUpdateViewLayout() {
            final TrashView trashView = mTrashView.get();
            if (trashView == null) {
                return;
            }
            // 删除图标(Trash Icon Root View)的移动极限设定(基于Gravity的基准位置计算)
            // 左下原点(画面下端(包括填充):0，向上方向:减，向下方向:正)，Y轴上限是删除图标位于背景中心的位置，下限是TrashIconRootView全部隐藏的位置
            final float density = trashView.mMetrics.density;
            final float backgroundHeight = trashView.mBackgroundView.getMeasuredHeight();
            final float offsetX = TRASH_MOVE_LIMIT_OFFSET_X * density;
            final int trashIconHeight = trashView.mTrashIconRootView.getMeasuredHeight();
            final int left = (int) -offsetX;
            final int top = (int) ((trashIconHeight - backgroundHeight) / 2 - TRASH_MOVE_LIMIT_TOP_OFFSET * density);
            final int right = (int) offsetX;
            final int bottom = trashIconHeight;
            mTrashIconLimitPosition.set(left, top, right, bottom);

            // 根据背景大小设定Y轴的追随范围
            mMoveStickyYRange = backgroundHeight * 0.20f;
        }
    }
}
