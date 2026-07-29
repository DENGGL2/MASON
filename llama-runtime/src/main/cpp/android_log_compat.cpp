#include "android_log_compat.h"

extern "C" int mason_android_log_is_loggable(
        int priority,
        const char *,
        int default_priority) {
    return priority >= default_priority;
}
