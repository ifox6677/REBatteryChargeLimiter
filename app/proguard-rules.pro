-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keepattributes Signature
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keep class io.github.zhangjq.bcl.ControlFile {
   *;
}

# This is safe for Android >= 2.0
-optimizations code/simplification/arithmetic

-optimizations !code/allocation/variable
-dontobfuscate