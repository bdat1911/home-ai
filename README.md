# Home AI

Home AI is a mobile application that supports interior-space design from images using artificial intelligence.

## Repository structure

- `FE/ReRoom`: Android application built with Kotlin and Jetpack Compose.
- `BE/InteriorAI`: ASP.NET Core API, EF Core, and AI image-generation integration.

## Local configuration

Do not commit API keys, database credentials, upload-service credentials, or local Android configuration files. Create local configuration files from the project documentation before running the application.

## Development workflow

The mobile app and API can be developed independently. Run the API first, configure the mobile application's base URL for the device or emulator being used, then run the Android application.
