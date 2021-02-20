package com.example.coolweather;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.coolweather.db.City;
import com.example.coolweather.db.County;
import com.example.coolweather.db.Province;
import com.example.coolweather.gson.Weather;
import com.example.coolweather.util.HttpUtil;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;
    private TextView title;
    private Button back;
    private ListView listView;
    private List<String> data = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ProgressDialog progressDialog;
    /**
     * 省、市、县列表
     */
    private List<Province> provinces;
    private List<City> cities;
    private List<County> counties;
    /**
     * 选中的省、市、级别
     */
    private Province selectedProvince;
    private City selectedCity;
    private int selectedLevel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        title = view.findViewById(R.id.title);
        listView = view.findViewById(R.id.listView);
        back = view.findViewById(R.id.back);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, data);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(selectedLevel == LEVEL_PROVINCE){
                    selectedProvince = provinces.get(position);
                    quertCites();
                }else if(selectedLevel == LEVEL_CITY){
                    selectedCity = cities.get(position);
                    queryCounties();
                } else if (selectedLevel == LEVEL_COUNTY) {
                    String weatherId = counties.get(position).getWeatherId();
                    if (getActivity() instanceof MainActivity) {
                        Intent intent = new Intent(getActivity(), WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    } else if (getActivity() instanceof  WeatherActivity) {
                        WeatherActivity activity = (WeatherActivity) getActivity();
                        activity.drawerLayout.closeDrawers();
                        activity.swipeRefresh.setRefreshing(true);
                        activity.requestWeather(weatherId);
                    }

                }
            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(selectedLevel == LEVEL_COUNTY){
                    quertCites();
                }else if(selectedLevel == LEVEL_CITY){
                    queryProvinces();
                }
            }
        });
        queryProvinces();
    }

    private void queryProvinces() {
        title.setText("中国");
        back.setVisibility(View.GONE);
        provinces = DataSupport.findAll(Province.class);
        if(provinces.size() > 0){
            data.clear();
            for (Province province : provinces) {
                data.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            selectedLevel = LEVEL_PROVINCE;
        }else{
            String url = "http://guolin.tech/api/china";
            queryFromServer(url,"province");
        }
    }

    private void quertCites() {
        title.setText(selectedProvince.getProvinceName());
        back.setVisibility(View.VISIBLE);
        cities = DataSupport.where("provinceid = ?",String.valueOf(selectedProvince.getId()))
                .find(City.class);
        if(cities.size() > 0){
            data.clear();
            for (City city:cities) {
                data.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            selectedLevel = LEVEL_CITY;
        }else{
            int provinceCode = selectedProvince.getProvinceCode();
            String url = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(url,"city");
        }
    }

    private void queryCounties() {
        title.setText(selectedCity.getCityName());
        back.setVisibility(View.VISIBLE);
        counties = DataSupport.where("cityid = ?",String.valueOf(selectedCity.getId()))
                .find(County.class);
        if(counties.size() > 0){
            data.clear();
            for (County county:counties) {
                data.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            selectedLevel = LEVEL_COUNTY;
        }else{
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String url = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(url,"county");
        }
    }


    private void queryFromServer(String url, final String type) {
        sendProgressDialog();
        OkHttpClient client = new OkHttpClient();
        Request build = new Request.Builder().url(url).build();
        client.newCall(build).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getContext(),"加载失败，请重试。", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                boolean result = false;
                if(type.equals("province")){
                    result = HttpUtil.handlerProvinceResponse(response.body().string());
                }else if(type.equals("city")){
                    result = HttpUtil.handlerCityResponse(response.body().string(),selectedProvince.getId());
                }else if(type.equals("county")){
                    result = HttpUtil.handlerCountyResponse(response.body().string(),selectedCity.getId());
                }
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(type.equals("province")){
                                queryProvinces();
                            }else if(type.equals("city")){
                                quertCites();
                            }else if(type.equals("county")){
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }

    private void sendProgressDialog() {
        if(progressDialog == null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
    }

    private void closeProgressDialog(){
        if(progressDialog != null){
            progressDialog.dismiss();
        }
    }

}
