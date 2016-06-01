package com.ibm.iot.android.iotstarter.utils;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.ibm.iot.android.iotstarter.R;

import java.util.List;

/**
 * Created by ser on 2016/5/30.
 */
public class TalkAdapter extends ArrayAdapter<String> {

    LayoutInflater lInflater;

    public TalkAdapter(Context context, int resource, List<String> objects) {
        super(context, resource, objects);
        lInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public TalkAdapter(Context context, int resource) {
        super(context, resource);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View view = convertView;
        if (view == null) {
            view = lInflater.inflate(R.layout.list_item, parent, false);
        }




        String message=getItem(position);
        if(message.contains("智能助手:")){
            ((TextView) view.findViewById(R.id.lblListItem)).setBackgroundResource(R.drawable.her);
            ((TextView) view.findViewById(R.id.lblListItem)).setText(message);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            ((TextView) view.findViewById(R.id.lblListItem)).setLayoutParams(lp);
            ((TextView) view.findViewById(R.id.lblListItem)).setGravity(Gravity.RIGHT);


        } else if(message.contains("我:")){
            ((TextView) view.findViewById(R.id.lblListItem)).setBackgroundResource(R.drawable.me);
            ((TextView) view.findViewById(R.id.lblListItem)).setText(message);

        } else{
            ((TextView) view.findViewById(R.id.lblListItem)).setText(message);
        }



        return view;
    }
}
