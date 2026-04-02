package ru.transaero21.telebug;

import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressWarnings("unused")
public class TelebugHook implements IXposedHookLoadPackage {
    private static final String TAG = "Telebug";

    private static final int CONSTRUCTOR_SEND_CODE = 0xa677244f;
    private static final int CONSTRUCTOR_PASSKEY_LOGIN = 0x518ad0b7;
    private static final int CONSTRUCTOR_EXPORT_LOGIN_TOKEN = 0xb7e085fe;

    private static final int TARGET_API_ID = 4;
    private static final String TARGET_API_HASH = "014b35b6184100b085b0d0572f9b5103";
    private static final String TARGET_REG_ID = "c4RO3vYgTgmnG2HtR0WZoK:APA91bGJ-niZl7i2aYwEh2Vg3ZMdRig1_GwZhMKEcPmQQ2GIc6wf-1KCUYxt7EzwzZBcV3o44G2QzMxuTHB5wM6WeWFsDYkE0uEPCFdMCXeF5iOv4_iTuE4";
    private static final String TARGET_FINGERPRINT = "49C1522548EBACD46CE322B6FD47F6092BB745D0F88082145CAF35E14DCC38E1";
    private static final String TARGET_PACKAGE_ID = "org.telegram.messenger.web";

    private static final int SENDCODE_SKIP_PHONE = 1;
    private static final int AWAIT_API_ID = 2;
    private static final int AWAIT_API_HASH = 3;

    private static final ConcurrentHashMap<Object, Integer> trackedBuffers = new ConcurrentHashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> nbbClass, cmClass;
        try {
            nbbClass = XposedHelpers.findClass("org.telegram.tgnet.NativeByteBuffer", lpparam.classLoader);
            cmClass = XposedHelpers.findClass("org.telegram.tgnet.ConnectionsManager", lpparam.classLoader);
        } catch (XposedHelpers.ClassNotFoundError e) {
            return;
        }

        Log.d(TAG, "Hooking into " + lpparam.packageName);

        try {
            XposedHelpers.findAndHookMethod(cmClass, "native_init",
                    int.class, int.class, int.class, int.class,
                    String.class, String.class, String.class, String.class, String.class,
                    String.class, String.class, String.class, String.class, String.class, String.class,
                    int.class, long.class, boolean.class, boolean.class, boolean.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Log.i(TAG, "native_init: replacing apiId, regId, fingerprint, packageId");
                                param.args[3] = TARGET_API_ID;
                                param.args[11] = TARGET_REG_ID;
                                param.args[12] = TARGET_FINGERPRINT;
                                param.args[14] = TARGET_PACKAGE_ID;
                            } catch (Throwable t) {
                                Log.e(TAG, "Error in native_init hook", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook native_init", t);
        }

        try {
            XposedHelpers.findAndHookMethod(nbbClass, "writeInt32", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        int value = (int) param.args[0];
                        Object buffer = param.thisObject;
                        int bufferHash = System.identityHashCode(buffer);

                        if (value == CONSTRUCTOR_SEND_CODE) {
                            Log.d(TAG, "sendCode detected, tracking buffer @" + bufferHash);
                            trackedBuffers.put(buffer, SENDCODE_SKIP_PHONE);
                            return;
                        }

                        if (value == CONSTRUCTOR_PASSKEY_LOGIN) {
                            Log.d(TAG, "initPasskeyLogin detected, tracking buffer @" + bufferHash);
                            trackedBuffers.put(buffer, AWAIT_API_ID);
                            return;
                        }

                        if (value == CONSTRUCTOR_EXPORT_LOGIN_TOKEN) {
                            Log.d(TAG, "exportLoginToken detected, tracking buffer @" + bufferHash);
                            trackedBuffers.put(buffer, AWAIT_API_ID);
                            return;
                        }

                        if (!trackedBuffers.isEmpty()) {
                            Integer state = trackedBuffers.get(buffer);
                            if (state == null) return;

                            if (state == AWAIT_API_ID) {
                                Log.i(TAG, "Replacing api_id: " + value + " -> " + TARGET_API_ID + " on buffer @" + bufferHash);
                                param.args[0] = TARGET_API_ID;
                                trackedBuffers.put(buffer, AWAIT_API_HASH);
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error in writeInt32 hook", t);
                    }
                }
            });
            Log.i(TAG, "writeInt32 hook installed for " + lpparam.packageName);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook writeInt32", t);
        }

        try {
            XposedHelpers.findAndHookMethod(nbbClass, "writeString", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (trackedBuffers.isEmpty()) return;

                        Object buffer = param.thisObject;
                        Integer state = trackedBuffers.get(buffer);
                        if (state == null) return;

                        int bufferHash = System.identityHashCode(buffer);

                        if (state == SENDCODE_SKIP_PHONE) {
                            Log.d(TAG, "Skipping phone_number on buffer @" + bufferHash);
                            trackedBuffers.put(buffer, AWAIT_API_ID);
                        } else if (state == AWAIT_API_HASH) {
                            String original = (String) param.args[0];
                            Log.i(TAG, "Replacing api_hash: " + original + " -> " + TARGET_API_HASH + " on buffer @" + bufferHash);
                            param.args[0] = TARGET_API_HASH;
                            trackedBuffers.remove(buffer);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error in writeString hook", t);
                    }
                }
            });
            Log.i(TAG, "writeString hook installed for " + lpparam.packageName);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook writeString", t);
        }
    }
}