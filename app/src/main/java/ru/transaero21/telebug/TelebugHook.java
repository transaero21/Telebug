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

    private static final int SENDCODE_SKIP_PHONE = 1;
    private static final int AWAIT_API_ID = 2;
    private static final int AWAIT_API_HASH = 3;

    private static final ConcurrentHashMap<Object, Integer> trackedBuffers = new ConcurrentHashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> nbbClass;
        try {
            nbbClass = XposedHelpers.findClass("org.telegram.tgnet.NativeByteBuffer", lpparam.classLoader);
        } catch (XposedHelpers.ClassNotFoundError e) {
            return;
        }

        Log.d(TAG, "Hooking into " + lpparam.packageName);

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

            Log.i(TAG, "Hooks installed successfully for " + lpparam.packageName);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install hooks for " + lpparam.packageName, t);
        }
    }
}