@echo off
echo ========================================================
echo   VisiLink-RAG: Multimodal Video RAG (Spring Boot 3.2)
echo   Grounded in Meng ^& Tong (Springer Nature, 2025)
echo ========================================================

cd /d "%~dp0"

if exist "target\visilink-rag-1.0.0.jar" (
    echo Starting VisiLink-RAG from packaged executable JAR...
    java -jar target\visilink-rag-1.0.0.jar
) else (
    echo Starting VisiLink-RAG via Maven Wrapper...
    call .\mvnw.cmd spring-boot:run
)

pause
