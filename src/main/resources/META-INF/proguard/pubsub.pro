# R8/ProGuard keep rules for com.aaravlabs:pubsub.
#
# The library reflects on annotated methods (@SubscribedTo, @RunPeriodically,
# @RunnableAction) and on Android's android.util.Log + the FTC SDK's Gamepad
# class. These rules ensure reflection paths survive minification.
#
# Include in your FTC project by adding this to your app's proguard-rules.pro
# (or via the consumer-rules.pro shipping with this artifact).

# Keep all annotation types — they're reflectively read by the binder.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisible*Annotations

# Keep the public API of the pubsub library.
-keep public class com.aaravlabs.pubsub.** { public *; }
-keep public interface com.aaravlabs.pubsub.** { *; }
-keep public class com.aaravlabs.pubsub.ftc.** { public *; }

# Keep internals (the annotation binder scans them by reflection).
-keep class com.aaravlabs.pubsub.internal.** { *; }

# Android's android.util.Log is reflectively invoked.
-keep class android.util.Log { *; }

# The FTC SDK's Gamepad class is reflectively scanned. Keep its public fields
# (boolean + float). Including all of Gamepad also keeps fields from inner
# classes that some configurations might expose.
-keep class com.qualcomm.robotcore.hardware.Gamepad { *; }
-keep class com.qualcomm.robotcore.hardware.Gamepad$* { *; }
