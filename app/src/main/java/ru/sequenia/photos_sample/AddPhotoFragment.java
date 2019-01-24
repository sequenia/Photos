package ru.sequenia.photos_sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.sequenia.navigation_router.NavigationRouter;
import com.sequenia.photo.Photos;
import com.sequenia.photo.listeners.ResultFromCamera;
import com.sequenia.photo.listeners.ResultFromGallery;
import com.squareup.picasso.Picasso;

import java.io.File;

public class AddPhotoFragment extends Fragment
        implements NavigationRouter.Replaceable, ResultFromCamera, ResultFromGallery {

    private Photos photos;
    private ImageView image;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        photos = new Photos(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        image = view.findViewById(R.id.photo);

        view.findViewById(R.id.open_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                photos.takePhotoFromCamera();
            }
        });

        view.findViewById(R.id.open_gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                photos.selectedPhotoFromGallery(false);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        photos.onResult(requestCode, resultCode, data);
    }

    @Override
    public void getPathFromCamera(String path) {
        showPhoto(path);
    }

    @Override
    public void getPathFromGallery(String path) {
        showPhoto(path);
    }

    @Override
    public void startIntentForPhoto(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
    }

    private void showPhoto(String path) {
        System.out.println("PHOTOS PATH " + path);
        Picasso.with(getContext())
                .load(new File(path))
                .into(image);
        // Абсолютны путь к файлу, можно отображать
    }
}
