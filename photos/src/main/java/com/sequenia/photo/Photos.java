package com.sequenia.photo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;

import com.gun0912.tedpermission.PermissionListener;
import com.sequenia.file.CursorUtils;
import com.sequenia.file.FilesUtils;
import com.sequenia.file.UriUtils;
import com.sequenia.photo.listeners.MultiResultFromGallery;
import com.sequenia.photo.listeners.PhotoErrors;
import com.sequenia.photo.listeners.PhotoWait;
import com.sequenia.photo.listeners.ResultFromCamera;
import com.sequenia.photo.listeners.ResultFromGallery;
import com.sequenia.photo.listeners.StartIntentForResult;
import com.sequenia.photo.repository.Repository;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ringo on 30.06.2016.
 * <p>
 * Класс, осуществляющий всю работу
 * с фотографиями
 * - добавить фотографию из галереи
 * - сделать фотографию с камеры
 */

public class Photos {

    private static final int GALLERY_REQUEST = 10101;
    private static final int TAKE_PHOTO_REQUEST = 20202;

    private static final int COUNT_REPEAT = 10;
    private static final int REPEAT_DELAY = 1000;

    private int lastEvent;                                  // Последнее действие (нужно для перезапуска, после выставления пермишенов)
    private boolean isMultiChoice;                          // Для выбора из галереи true - множественный

    private String filePath;                                // Путь к файлу

    private PhotoErrors errors;                             // Слушатели на ошибки
    private ResultFromCamera resultPathFormCamera;          // Возвращение рузультата с камеры
    private ResultFromGallery resultFromGallery;            // Возвращение рузультата из галлереи
    private MultiResultFromGallery multiResultFromGallery;  // Возвращение рузультата из галлереи
    private PhotoWait photoWait;                            // Показатель ожидания
    private StartIntentForResult intentForResult;           // Интерфейс на реализацию метода открытия intent

    private WeakReference<Context> weakReferenceContext;

    public Photos(Activity activity) {
        setContext(activity);

        if (activity instanceof ResultFromCamera) {
            resultPathFormCamera = (ResultFromCamera) activity;
        }

        if (activity instanceof ResultFromGallery) {
            resultFromGallery = (ResultFromGallery) activity;
        }

        if (activity instanceof PhotoErrors) {
            errors = (PhotoErrors) activity;
        }

        if (activity instanceof PhotoWait) {
            photoWait = (PhotoWait) activity;
        }

        if (activity instanceof MultiResultFromGallery) {
            multiResultFromGallery = (MultiResultFromGallery) activity;
        }

        if (activity instanceof StartIntentForResult) {
            intentForResult = (StartIntentForResult) activity;
        }
    }

    public Photos(Fragment fragment) {
        setContext(fragment.getContext());

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

    private void setContext(Context context) {
        weakReferenceContext = new WeakReference<>(context);
    }

    private Context getContext() {
        if(weakReferenceContext == null) {
            return null;
        }

        return weakReferenceContext.get();
    }

    /**
     * Выбор фотографий из галереи
     *
     * @param isMultiChoice - true - множественный выбор
     */
    public void selectedPhotoFromGallery(boolean isMultiChoice) {
        lastEvent = GALLERY_REQUEST;
        this.isMultiChoice = isMultiChoice;

        Context context = getContext();
        if(context != null) {
            PermissionManager.storagePermission(context, getPermissionListener());
        }
    }

    /**
     * Выбор фотографий из галереи
     */
    private void openGallery() {
        if(intentForResult == null) {
            showIntentError();
            return;
        }

        Intent intent = new Intent();
        intent.setType("image/*");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiChoice);
        }
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intentForResult.startIntentForPhoto(
                Intent.createChooser(intent, getText(R.string.add_photo)),
                GALLERY_REQUEST
        );
    }

    /**
     * Сделать фото с камеры
     */
    public void takePhotoFromCamera() {
        lastEvent = TAKE_PHOTO_REQUEST;
        Context context = getContext();
        if(context != null) {
            PermissionManager.storagePermission(context, getPermissionListener());
        }
    }

    /**
     * Сделать фото с камеры
     */
    private void openCamera() {
        try {
            Context context = getContext();
            if(context == null) {
                return;
            }

            if(intentForResult == null) {
                showIntentError();
                return;
            }

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(context.getPackageManager()) == null) {
                showCameraError(getText(R.string.not_found_camera));
                return;
            }

            File image = FilesUtils.createJPGFileInOpenDirectory(context);

            if(image == null) {
                showCameraError(getText(R.string.create_file_error));
                return;
            }

            filePath = image.getAbsolutePath();
            // Сохраняем, чтобы восстановить, если экран потеряется
            Repository.savePath(context, filePath);

            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                    UriUtils.getFileUri(context, image));
            intentForResult.startIntentForPhoto(takePictureIntent, TAKE_PHOTO_REQUEST);

        } catch (IOException exc) {
            showCameraError(exc.getMessage());
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
        Context context = getContext();
        if(context == null) {
            return;
        }

        // возможно, экран пересоздался
        if(filePath == null) {
            filePath = Repository.getPath(context);
            // подчищаем все, что хранится во временном хранилище
            Repository.removePath(context);
        }

        if(filePath == null) {
            showCameraError(getText(R.string.take_photo_path_null));
            return;
        }

        // если файл существует и его размер больше 0
        if (FilesUtils.checkedFile(filePath)) {
            returnResultFromCamera(filePath);
            return;
        }

        setPhotoWaitState(true);
        getPhotoPathAsync(0);
    }

    /**
     * Достать путь к выбранной фотографии (фотографиям)
     *
     * @param data - хранится информация
     */
    private void photoFromGallery(Intent data) {
        List<Uri> uris = UriUtils.getUrisFromData(data);
        if (uris == null || uris.isEmpty()) {
            showGalleryError(getText(R.string.take_photo_path_not_find));
            return;
        }
        returnResultFromGallery(uris);
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
                if (FilesUtils.checkedFile(filePath)) {
                    setPhotoWaitState(false);
                    returnResultFromCamera(filePath);
                    return;
                }

                if (tryCount >= COUNT_REPEAT) {
                    setPhotoWaitState(false);
                    getPathLastPhoto();
                    return;
                }

                getPhotoPathAsync(tryCount + 1);
            }
        }, REPEAT_DELAY);
    }

    /**
     * Попытка достать последнюю
     * фотографию из галлереи
     */
    private void getPathLastPhoto() {
        Context context = getContext();
        if(context == null) {
            return;
        }

        filePath = CursorUtils.getLastImageFile(context);

        if(filePath == null) {
            showCameraError(getText(R.string.take_photo_path_null));
            return;
        }

        returnResultFromCamera(filePath);
    }

    /**
     * Возвращение результата из галереи
     *
     * @param uris - список URIS выбранных файлов
     */
    private void returnResultFromGallery(List<Uri> uris) {
        Context context = getContext();
        if(context == null) {
            return;
        }

        List<String> paths = new ArrayList<>();
        for (Uri uri : uris) {
            paths.add(UriUtils.getPath(context, uri));
        }

        if (paths.size() == 1) {
            if (resultFromGallery != null) {
                resultFromGallery.getPathFromGallery(paths.get(0));
            }
        }

        if (multiResultFromGallery != null) {
            multiResultFromGallery.getPathsFromGallery(paths);
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
        }
    }

    private void showGalleryError(String error) {
        if (errors != null) {
            errors.errorSelectedPhotoFromGallery(error);
        }
    }

    private void showCameraError(String error) {
        if (errors != null) {
            errors.errorTakePhotoFromCamera(error);
        }
    }

    private void setPhotoWaitState(boolean state) {
        if (photoWait != null) {
            photoWait.visibilityWait(state);
        }
    }

    private void showIntentError() {
        String error = getText(R.string.intent_error);
        showCameraError(error);
        showGalleryError(error);
    }

    private String getText(int res) {
        Context context = getContext();
        if(context == null) {
            return null;
        }

        return context.getString(res);
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
            public void onPermissionDenied(List<String> deniedPermissions) {

            }
        };
    }
}
