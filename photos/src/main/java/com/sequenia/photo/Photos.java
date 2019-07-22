package com.sequenia.photo;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;

import com.gun0912.tedpermission.PermissionListener;
import com.sequenia.photo.listeners.MultiResultFromGallery;
import com.sequenia.photo.listeners.PhotoErrors;
import com.sequenia.photo.listeners.PhotoWait;
import com.sequenia.photo.listeners.ResultFromCamera;
import com.sequenia.photo.listeners.ResultFromGallery;
import com.sequenia.photo.listeners.StartIntentForResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ringo on 30.06.2016.
 * Класс, осуществляющий всю работу
 * с фотографиями
 * - добавить фотографию из галереи
 * - сделать фотографию с камеры
 */

public class Photos {

    private static final int GALLERY_REQUEST = 10101;
    private static final int TAKE_PHOTO_REQUEST = 20202;

    private int lastEvent;                                  // Последнее действие (нужно для перезапуска, после выставления пермишенов)
    private boolean isMultiChoice;                          // Для выбора из галереи true - множественный

    private String filePath;                                // Путь к файлу

    private PhotoErrors errors;                             // Слушатели на ошибки
    private ResultFromCamera resultPathFormCamera;          // Возвращение рузультата с камеры
    private ResultFromGallery resultFromGallery;            // Возвращение рузультата из галлереи
    private MultiResultFromGallery multiResultFromGallery;  // Возвращение рузультата из галлереи
    private PhotoWait photoWait;                            // Показатель ожидания
    private StartIntentForResult intentForResult;           // Интерфейс на реализацию метода открытия intent

    private Context context;

    public Photos(Activity activity) {
        this.context = activity;

        if (context instanceof ResultFromCamera) {
            resultPathFormCamera = (ResultFromCamera) context;
        }

        if (context instanceof ResultFromGallery) {
            resultFromGallery = (ResultFromGallery) context;
        }

        if (context instanceof PhotoErrors) {
            errors = (PhotoErrors) context;
        }

        if (context instanceof PhotoWait) {
            photoWait = (PhotoWait) context;
        }

        if (context instanceof MultiResultFromGallery) {
            multiResultFromGallery = (MultiResultFromGallery) context;
        }

        if (context instanceof StartIntentForResult) {
            intentForResult = (StartIntentForResult) context;
        }
    }

    public Photos(Fragment fragment) {
        this.context = fragment.getContext();

        if (fragment instanceof ResultFromCamera) {
            resultPathFormCamera = (ResultFromCamera) fragment;
        }

        if (fragment instanceof ResultFromGallery) {
            resultFromGallery = (ResultFromGallery) fragment;
        }

        if (fragment instanceof PhotoErrors) {
            errors = (PhotoErrors) fragment;
        }

        if (fragment instanceof PhotoWait) {
            photoWait = (PhotoWait) fragment;
        }

        if (fragment instanceof MultiResultFromGallery) {
            multiResultFromGallery = (MultiResultFromGallery) fragment;
        }

        if (fragment instanceof StartIntentForResult) {
            intentForResult = (StartIntentForResult) fragment;
        }
    }

    /**
     * Выбор фотографий из галереи
     *
     * @param isMultiChoice - true - множественный выбор
     */
    public void selectedPhotoFromGallery(boolean isMultiChoice) {
        lastEvent = GALLERY_REQUEST;
        this.isMultiChoice = isMultiChoice;
        PermissionManager.storagePermission(context, getPermissionListener());
    }

    /**
     * Выбор фотографий из галереи
     */
    private void openGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiChoice);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        if (intentForResult != null) {
            intentForResult.startIntentForPhoto(
                    Intent.createChooser(intent, getText(R.string.add_photo)), GALLERY_REQUEST
            );
        }
    }

    /**
     * Сделать фото с камеры
     */
    public void takePhotoFromCamera() {
        lastEvent = TAKE_PHOTO_REQUEST;
        PermissionManager.storagePermission(context, getPermissionListener());
    }

    /**
     * Сделать фото с камеры
     */
    private void openCamera() {
        try {
            File image = FilesManager.createJPGFileInOpenDirectory(context);
            filePath = image.getAbsolutePath();

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri uri = Uri.fromFile(image);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            } else {
                File file = new File(uri.getPath());
                Uri photoUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".provider", file);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            }
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intentForResult != null &&
                    takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
                intentForResult.startIntentForPhoto(takePictureIntent, TAKE_PHOTO_REQUEST);
            }
        } catch (IOException exc) {
            errors.errorTakePhotoFromCamera(exc.getMessage());
        }
    }

    /**
     * Обработка результата добавления фотографии
     *
     * @param data - результат
     */
    public void onResult(int requestCode, int resultCode, Intent data) {
        // получили данные о дейсвиях
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case GALLERY_REQUEST:
                    photoFromGallery(data);
                    break;
                case TAKE_PHOTO_REQUEST:
                    photoFromCamera();
                    break;
            }
        }
    }

    /**
     * Достать путь к уже сделанной фотографии
     */
    private void photoFromCamera() {
        // файл для сохранения фотографии не создался
        if (filePath != null) {
            // если файл существует и его размер больше 0
            if (FilesManager.checkedFile(filePath)) {
                returnResultFromCamera(filePath);
            } else {
                setPhotoWaitVisibility(true);
                getPhotoPathAsync(0);
            }
        } else {
            filePath = null;
            errors.errorTakePhotoFromCamera(getText(R.string.take_photo_path_null));
        }
    }

    /**
     * Достать путь к выбранной фотографии (фотографиям)
     *
     * @param data - хранится информация
     */
    private void photoFromGallery(Intent data) {
        try {
            if (null == data.getData()) {
                ClipData clipdata = data.getClipData();

                if (clipdata != null) {
                    ArrayList<Uri> uris = new ArrayList<>();
                    for (int i = 0; i < clipdata.getItemCount(); i++) {
                        uris.add(clipdata.getItemAt(i).getUri());
                    }

                    if (uris.size() > 1) {
                        returnResultFromGallery(uris);
                    } else {
                        if (uris.size() > 0) {
                            returnResultFromGallery(uris);
                        } else {
                            errors.errorSelectedPhotoFromGallery(getText(R.string.take_photo_path_not_find));
                        }
                    }
                }
            } else {
                Uri uri = data.getData();
                if (uri != null) {
                    returnResultFromGallery(data.getData());
                } else {
                    errors.errorSelectedPhotoFromGallery(getText(R.string.take_photo_path_not_find));
                }
            }
        } catch (SecurityException se) {
            se.printStackTrace();
            errors.errorSelectedPhotoFromGallery(se.getMessage());
        }
    }

    /**
     * Возвращение пути с камеры
     *
     * @param path - путь с камеры
     */
    private void returnResultFromCamera(String path) {
        if (resultPathFormCamera != null) {
            resultPathFormCamera.getPathFromCamera(path);
            // путь вернули и затираем тот, который сохранен
            filePath = null;
        }
    }

    /**
     * Возвращение результата из галереи
     *
     * @param uri - путь из галереи
     */
    private void returnResultFromGallery(Uri uri) {
        setPhotoWaitVisibility(true);
        FilesManager.getPath(context, uri, new FilesManager.GetPathCallback() {
            @Override
            public void onSuccess(String path) {
                setPhotoWaitVisibility(false);
                if (resultFromGallery != null) {
                    resultFromGallery.getPathFromGallery(path);
                }
            }

            @Override
            public void onOutOfMemoryError() {
                setPhotoWaitVisibility(false);
                if (errors != null) {
                    errors.errorSelectedPhotoFromGallery(
                            getText(R.string.take_photo_out_of_memory_error));
                }
            }

            @Override
            public void onError(String error) {
                setPhotoWaitVisibility(false);
                if (errors != null) {
                    errors.errorSelectedPhotoFromGallery(error);
                }
            }
        });

    }

    private void setPhotoWaitVisibility(boolean isVisible) {
        if (photoWait != null) {
            photoWait.visibilityWait(isVisible);
        }
    }

    private void returnResultFromGallery(ArrayList<Uri> uris) {
        if (uris.isEmpty()) {
            if (errors != null) {
                errors.errorSelectedPhotoFromGallery(
                        getText(R.string.take_photo_path_null));
            }
            return;
        }
        returnResultFromGallery(uris.get(0));
    }

    /**
     * Ожидание появление фотографии в альбоме
     *
     * @param tryCount - количество попыток 10
     */
    private void getPhotoPathAsync(final int tryCount) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (FilesManager.checkedFile(filePath)) {
                    setPhotoWaitVisibility(false);
                    returnResultFromCamera(filePath);
                } else {
                    if (tryCount >= 10) {
                        setPhotoWaitVisibility(false);
                        if (errors != null) {
                            getPathLastPhoto();
                        }
                    } else {
                        getPhotoPathAsync(tryCount + 1);
                    }
                }
            }
        }, 1000);
    }

    /**
     * Попытка достать последнюю
     * фотографию из галлереи
     */
    private void getPathLastPhoto() {
        filePath = getLastPhotoInGallery();
        if (filePath != null) {
            returnResultFromCamera(filePath);
        } else {
            errors.errorTakePhotoFromCamera(getText(R.string.take_photo_path_null));
        }
    }

    /**
     * @return путь к последней фотографии из галереи
     */
    private String getLastPhotoInGallery() {
        String path = null;
        // Find the last picture
        String[] projection = new String[]{
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Images.ImageColumns.DATE_TAKEN,
                MediaStore.Images.ImageColumns.MIME_TYPE
        };

        final Cursor cursor = context.getContentResolver()
                .query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null,
                        null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC");

        // Put it in the image view
        if (cursor != null && cursor.moveToFirst()) {
            path = cursor.getString(1);
            cursor.close();
        }

        return path;
    }

    private PermissionListener getPermissionListener() {
        return new PermissionListener() {
            @Override
            public void onPermissionGranted() {
                // выдали разрешение, повторяем попытку запуска
                switch (lastEvent) {
                    case GALLERY_REQUEST:
                        openGallery();
                        break;
                    case TAKE_PHOTO_REQUEST:
                        openCamera();
                        break;
                }
            }

            @Override
            public void onPermissionDenied(ArrayList<String> deniedPermissions) {

            }
        };
    }

    private String getText(int res) {
        return context.getString(res);
    }
}
