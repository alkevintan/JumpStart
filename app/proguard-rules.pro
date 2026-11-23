# Compose and AndroidX ship their own consumer rules. The app uses no reflection, no JNI and no serialization
# libraries: the SQLite dictionary uses the framework android.database.sqlite API directly.
# Keep line numbers so crash stack traces from release builds stay readable.
-keepattributes SourceFile,LineNumberTable
