/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gamespace.runtime;

import android.content.Context;
import android.os.CpuUsageInfo;
import android.os.GpuHeadroomParams;
import android.os.HardwarePropertiesManager;
import android.os.health.SystemHealthManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class SystemStatsSampler {
    static final class Snapshot {
        final float cpuUsagePercent;
        final float cpuTempCelsius;
        final float gpuUsagePercent;
        final float fps;

        Snapshot(float cpuUsagePercent, float cpuTempCelsius, float gpuUsagePercent, float fps) {
            this.cpuUsagePercent = cpuUsagePercent;
            this.cpuTempCelsius = cpuTempCelsius;
            this.gpuUsagePercent = gpuUsagePercent;
            this.fps = fps;
        }
    }

    private final HardwarePropertiesManager mHardwarePropertiesManager;
    @Nullable
    private final SystemHealthManager mSystemHealthManager;
    @Nullable
    private final GpuHeadroomParams mGpuHeadroomParams;
    private long[] mLastCpuActive;
    private long[] mLastCpuTotal;

    SystemStatsSampler(@NonNull Context context) {
        mHardwarePropertiesManager = context.getSystemService(HardwarePropertiesManager.class);
        mSystemHealthManager = context.getSystemService(SystemHealthManager.class);
        mGpuHeadroomParams = buildGpuHeadroomParams();
    }

    @NonNull
    Snapshot sample(float fps) {
        return new Snapshot(
                readCpuUsagePercent(),
                readCpuTemperature(),
                readGpuUsagePercent(),
                fps);
    }

    private float readCpuUsagePercent() {
        if (mHardwarePropertiesManager == null) {
            return Float.NaN;
        }
        final CpuUsageInfo[] usages;
        try {
            usages = mHardwarePropertiesManager.getCpuUsages();
        } catch (SecurityException e) {
            return Float.NaN;
        } catch (RuntimeException e) {
            return Float.NaN;
        }
        if (usages == null || usages.length == 0) {
            return Float.NaN;
        }

        long[] active = new long[usages.length];
        long[] total = new long[usages.length];
        for (int i = 0; i < usages.length; i++) {
            CpuUsageInfo usage = usages[i];
            if (usage == null) {
                continue;
            }
            active[i] = usage.getActive();
            total[i] = usage.getTotal();
        }

        if (mLastCpuActive == null || mLastCpuTotal == null || mLastCpuActive.length != active.length) {
            mLastCpuActive = active;
            mLastCpuTotal = total;
            return Float.NaN;
        }

        long activeDelta = 0L;
        long totalDelta = 0L;
        for (int i = 0; i < active.length; i++) {
            long deltaActive = Math.max(0L, active[i] - mLastCpuActive[i]);
            long deltaTotal = Math.max(0L, total[i] - mLastCpuTotal[i]);
            activeDelta += deltaActive;
            totalDelta += deltaTotal;
        }
        mLastCpuActive = active;
        mLastCpuTotal = total;

        if (totalDelta <= 0L) {
            return Float.NaN;
        }
        return Math.max(0f, Math.min(100f, (activeDelta * 100f) / totalDelta));
    }

    private float readCpuTemperature() {
        if (mHardwarePropertiesManager == null) {
            return Float.NaN;
        }
        final float[] temperatures;
        try {
            temperatures = mHardwarePropertiesManager.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT);
        } catch (SecurityException e) {
            return Float.NaN;
        } catch (RuntimeException e) {
            return Float.NaN;
        }
        if (temperatures == null || temperatures.length == 0) {
            return Float.NaN;
        }
        float sum = 0f;
        int count = 0;
        for (float temperature : temperatures) {
            if (temperature != HardwarePropertiesManager.UNDEFINED_TEMPERATURE) {
                sum += temperature;
                count++;
            }
        }
        return count == 0 ? Float.NaN : sum / count;
    }

    private float readGpuUsagePercent() {
        if (mSystemHealthManager == null) {
            return Float.NaN;
        }
        try {
            float headroom = mSystemHealthManager.getGpuHeadroom(mGpuHeadroomParams);
            if (Float.isNaN(headroom)) {
                return Float.NaN;
            }
            return Math.max(0f, Math.min(100f, 100f - headroom));
        } catch (UnsupportedOperationException e) {
            return Float.NaN;
        } catch (RuntimeException e) {
            return Float.NaN;
        }
    }

    @Nullable
    private GpuHeadroomParams buildGpuHeadroomParams() {
        try {
            return new GpuHeadroomParams.Builder()
                    .setCalculationType(GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_AVERAGE)
                    .build();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
