package org.cubewhy.skidcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.cubewhy.skidcamera.ui.theme.SkidCameraTheme
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// The main activity of the application.
class MainActivity : ComponentActivity() {
    // Stores the URIs of all captured images for batch sharing.
    private val capturedImageUris = mutableListOf<Uri>()
    // Stores the URI for the photo currently being taken.
    private var latestPhotoUri: Uri? = null
    // State to track the number of captured photos for UI update.
    private var photoCount by mutableStateOf(0)

    // Directory to store temporary image files.
    private val imagesDir by lazy { File(cacheDir, "images") }

    // Launcher for taking a picture using the system camera app.
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        // Check if the picture was successfully taken.
        if (success) {
            // Add the URI of the newly captured image to the list for batch sharing.
            latestPhotoUri?.let { uri ->
                capturedImageUris.add(uri)
                photoCount = capturedImageUris.size // Update the state
                Toast.makeText(this, "Photo captured. Total: ${capturedImageUris.size}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // User manually cancelled or failed to take the picture.
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
        }
        // Reset the latest URI as the capture attempt is finished.
        latestPhotoUri = null
    }

    // Launcher for requesting the camera permission.
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            launchCamera() // Launch camera after permission is granted.
        } else {
            Toast.makeText(this, "User denied the camera permission", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Clear previous temporary images and reset the list of URIs on app start.
        clearImages()
        capturedImageUris.clear()
        photoCount = 0

        enableEdgeToEdge()
        setContent {
            SkidCameraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass the current photo count and callbacks to the Composable screen.
                    CameraShareScreen(
                        photoCount = photoCount,
                        onTakePhotoClick = {
                            checkCameraPermissionAndLaunch()
                        },
                        onShareAllDirectlyClick = {
                            shareImagesDirectly() // Share all as separate images
                        },
                        onShareAsArchiveClick = {
                            createAndShareArchive() // Package all as a single file and share
                        }
                    )
                }
            }
        }
    }

    /**
     * Deletes all temporary image files and any created archive file in the cache directory.
     */
    private fun clearImages() {
        imagesDir.deleteRecursively()
    }

    /**
     * Checks for camera permission and either launches the camera or requests permission.
     */
    private fun checkCameraPermissionAndLaunch() {
        when {
            // Permission is already granted.
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            // Should show an explanation (for API 23+).
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            // Request the permission directly.
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Creates a temporary image file URI and launches the system camera app.
     */
    private fun launchCamera() {
        val photoUri = createImageUri()
        if (photoUri != null) {
            latestPhotoUri = photoUri // Store the URI before launching the camera
            takePictureLauncher.launch(photoUri)
        } else {
            Toast.makeText(this, "Failed to create temp image file", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Creates a unique File URI in the cache directory for the camera app to write the image.
     */
    private fun createImageUri(): Uri? {
        // Create a unique file name.
        val imageFile = File(
            imagesDir,
            "temp_image_${System.currentTimeMillis()}.jpg"
        )
        val imagePath = imageFile.parentFile
        // Ensure the directory exists.
        if (imagePath != null && !imagePath.exists()) {
            imagePath.mkdirs()
        }
        // Get the content URI using FileProvider for security.
        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            imageFile
        )
    }

    /**
     * Shares all captured images directly using ACTION_SEND_MULTIPLE.
     */
    private fun shareImagesDirectly() {
        if (capturedImageUris.isEmpty()) {
            Toast.makeText(this, "No images to share", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE // Use for sharing multiple items
            // Pass the list of URIs as a ParcelableArrayListExtra.
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(capturedImageUris))
            type = "image/*" // Mime type for general images.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant read access to the URIs
        }

        val chooser = Intent.createChooser(shareIntent, "Share ${capturedImageUris.size} photos...")
        startActivity(chooser)

        capturedImageUris.clear()
        photoCount = 0 // Reset the UI count
        Toast.makeText(this, "Sharing initiated and temporary files cleared.", Toast.LENGTH_SHORT).show()
    }

    /**
     * Packages all captured images into a single ZIP file (as a standard alternative to tar.gz) and shares it.
     * Note: Creating a full tar.gz requires external libraries (e.g., Apache Commons Compress).
     * We use the built-in ZipOutputStream for simple, standard archival.
     */
    private fun createAndShareArchive() {
        if (capturedImageUris.isEmpty()) {
            Toast.makeText(this, "No images to archive", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Create the output archive file (using .zip for simplicity and standard Android API support).
        val archiveFile = File(imagesDir, "batch_photos_${System.currentTimeMillis()}.zip")
        archiveFile.parentFile?.mkdirs() // Ensure directory exists

        try {
            // 2. Write the ZIP file.
            ZipOutputStream(FileOutputStream(archiveFile)).use { zipOut ->
                capturedImageUris.forEachIndexed { index, uri ->
                    // Open input stream for the image file.
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        // Create a unique entry name for the image in the archive.
                        val entryName = "photo_${index + 1}.jpg"
                        zipOut.putNextEntry(ZipEntry(entryName))

                        // Copy the image content to the zip entry.
                        inputStream.copyTo(zipOut)

                        zipOut.closeEntry()
                    }
                }
            }

            // 3. Get the FileProvider URI for the new archive file.
            val archiveUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                archiveFile
            )

            // 4. Share the archive file.
            shareArchive(archiveUri)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to create archive: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            archiveFile.delete() // Clean up failed archive file
        }
    }

    /**
     * Shares a single file (the archive).
     */
    private fun shareArchive(archiveUri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, archiveUri)
            type = "application/zip" // Mime type for the ZIP archive
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share Archive...")
        startActivity(chooser)

        // After sharing, clear the list and the temp files (including the archive).
        capturedImageUris.clear()
        photoCount = 0 // Reset the UI count
        Toast.makeText(this, "Archive sharing initiated and temporary files cleared.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Composable screen for taking photos and sharing them.
 *
 * @param photoCount The current number of photos taken.
 * @param onTakePhotoClick Callback for when the "Take a photo" button is clicked.
 * @param onShareAllDirectlyClick Callback for when the "Share All Images" button is clicked.
 * @param onShareAsArchiveClick Callback for when the "Share as Archive" button is clicked.
 */
@Composable
fun CameraShareScreen(
    photoCount: Int,
    onTakePhotoClick: () -> Unit,
    onShareAllDirectlyClick: () -> Unit,
    onShareAsArchiveClick: () -> Unit
) {
    Column( // Use Column to stack buttons vertically.
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Button to launch the camera.
        Button(onClick = onTakePhotoClick) {
            Text(text = "Take a photo", fontSize = 20.sp)
        }

        // Only show sharing options if photos have been taken.
        if (photoCount > 0) {
            Spacer(modifier = Modifier.height(24.dp))

            // Option 1: Share all images separately (multiple files).
            Button(onClick = onShareAllDirectlyClick) {
                Text(text = "Share All Images ($photoCount)", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option 2: Package and share as a single archive (ZIP).
            Button(onClick = onShareAsArchiveClick) {
                Text(text = "Share as Archive (ZIP)", fontSize = 20.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SkidCameraTheme {
        CameraShareScreen(
            photoCount = 3,
            onTakePhotoClick = {},
            onShareAllDirectlyClick = {},
            onShareAsArchiveClick = {}
        )
    }
}