#pragma once

#include <android/log.h>

extern "C" int mason_android_log_is_loggable(
        int priority,
        const char *tag,
        int default_priority);

#define __android_log_is_loggable mason_android_log_is_loggable
