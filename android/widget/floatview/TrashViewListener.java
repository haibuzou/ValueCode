/**
 * Copyright 2015 RECRUIT LIFESTYLE CO., LTD.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *            http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taptap.common.widget.floatview;

/**
 * 是处理TrashView事件的接口。
 * INFO:因为删除图标追随的规范，OPEN动画的动画结束不会被通知。
 */
interface TrashViewListener {

    /**
     * Require ActionTrashIcon updates.
     */
    void onUpdateActionTrashIcon();

    /**
     * 开始动画的时候被通知。
     *
     * @param animationCode 动画代码
     */
    void onTrashAnimationStarted(int animationCode);

    /**
     * 动画结束的时候会被通知。
     *
     * @param animationCode 动画代码
     */
    void onTrashAnimationEnd(int animationCode);


}
