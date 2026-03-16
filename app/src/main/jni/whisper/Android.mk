# whisper.cpp Android.mk — builds libwhisper_jni.so
# Vendored from whisper.cpp v1.8.3 (30c5194)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := whisper_jni

# C++17 required by ggml (std::filesystem in backend registry)
LOCAL_CPP_FEATURES := exceptions
LOCAL_CFLAGS   += -O3 -DNDEBUG -D_XOPEN_SOURCE=600 -D_GNU_SOURCE \
    -DGGML_USE_CPU \
    -DWHISPER_VERSION=\"1.8.3\" \
    -DGGML_VERSION=\"0.9.7\" \
    -DGGML_COMMIT=\"30c5194\" \
    -pthread
LOCAL_CPPFLAGS += -std=c++17

# Include paths
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/ggml/include \
    $(LOCAL_PATH)/ggml/src \
    $(LOCAL_PATH)/ggml/src/ggml-cpu

# --- whisper sources ---
LOCAL_SRC_FILES := \
    whisper.cpp \
    whisper_jni.cpp

# --- ggml base sources ---
LOCAL_SRC_FILES += \
    ggml/src/ggml-c.c \
    ggml/src/ggml.cpp \
    ggml/src/ggml-alloc.c \
    ggml/src/ggml-backend.cpp \
    ggml/src/ggml-opt.cpp \
    ggml/src/ggml-threading.cpp \
    ggml/src/ggml-quants.c \
    ggml/src/gguf.cpp

# --- ggml backend registry ---
LOCAL_SRC_FILES += \
    ggml/src/ggml-backend-reg.cpp \
    ggml/src/ggml-backend-dl.cpp

# --- ggml-cpu core sources ---
LOCAL_SRC_FILES += \
    ggml/src/ggml-cpu/ggml-cpu-c.c \
    ggml/src/ggml-cpu/ggml-cpu.cpp \
    ggml/src/ggml-cpu/repack.cpp \
    ggml/src/ggml-cpu/hbm.cpp \
    ggml/src/ggml-cpu/quants.c \
    ggml/src/ggml-cpu/traits.cpp \
    ggml/src/ggml-cpu/binary-ops.cpp \
    ggml/src/ggml-cpu/unary-ops.cpp \
    ggml/src/ggml-cpu/vec.cpp \
    ggml/src/ggml-cpu/ops.cpp \
    ggml/src/ggml-cpu/amx/amx.cpp \
    ggml/src/ggml-cpu/amx/mmq.cpp

# --- ggml-cpu ARM architecture ---
LOCAL_SRC_FILES += \
    ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp \
    ggml/src/ggml-cpu/arch/arm/quants.c \
    ggml/src/ggml-cpu/arch/arm/repack.cpp

LOCAL_LDLIBS := -llog -landroid -ldl

include $(BUILD_SHARED_LIBRARY)
