# jaudiotagger reads tag frames by reflection and refers to desktop Java classes it never uses on
# Android (artwork through java.awt and javax.imageio).
-keep class org.jaudiotagger.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
