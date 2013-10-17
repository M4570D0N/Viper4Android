LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += roottools
LOCAL_REQUIRED_MODULES := libV4AJniUtils

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := ViPER4Android

LOCAL_OVERRIDES_PACKAGES := MusicFX

include $(BUILD_PACKAGE)
##################################################
include $(CLEAR_VARS)
LOCAL_MODULE := libV4AJniUtils
LOCAL_SRC_FILES := libs/armeabi/libV4AJniUtils.so
include $(PREBUILT_SHARED_LIBRARY)

##################################################
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := roottools:libs/RootTools-2.6.jar

include $(BUILD_MULTI_PREBUILT)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
