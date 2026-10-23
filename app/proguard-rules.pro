# 1단계에서는 추가 규칙이 없다.
# proguard-android-optimize.txt 의 기본 규칙과 각 라이브러리가 제공하는 consumer 규칙을 쓴다.
# 난독화된 스택트레이스를 읽기 위해 매핑 파일을 남긴다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
