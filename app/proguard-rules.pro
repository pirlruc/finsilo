# SQLCipher JNI and the app's Room entities. Generated Room code does not need
# a blanket keep on androidx.room or WorkManager.
-keep class net.zetetic.sqlcipher.** { *; }
-keep class com.pirlruc.finsilo.data.local.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.**
