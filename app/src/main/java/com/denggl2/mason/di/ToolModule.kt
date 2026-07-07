package com.denggl2.mason.di

import com.denggl2.mason.tool.BatteryTool
import com.denggl2.mason.tool.BluetoothTool
import com.denggl2.mason.tool.CpuTool
import com.denggl2.mason.tool.DeviceInfoTool
import com.denggl2.mason.tool.GpuTool
import com.denggl2.mason.tool.MemoryTool
import com.denggl2.mason.tool.SensorTool
import com.denggl2.mason.tool.ToolRegistry
import com.denggl2.mason.tool.WifiTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ToolModule {

    @Provides
    @Singleton
    fun provideToolRegistry(
        cpuTool: CpuTool,
        gpuTool: GpuTool,
        batteryTool: BatteryTool,
        memoryTool: MemoryTool,
        sensorTool: SensorTool,
        bluetoothTool: BluetoothTool,
        wifiTool: WifiTool,
        deviceInfoTool: DeviceInfoTool,
    ): ToolRegistry {
        return ToolRegistry().apply {
            registerAll(setOf(
                deviceInfoTool,
                cpuTool,
                gpuTool,
                batteryTool,
                memoryTool,
                sensorTool,
                bluetoothTool,
                wifiTool,
            ))
        }
    }
}
