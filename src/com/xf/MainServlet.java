package com.xf;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.iflytek.msp.cpdb.lfasr.client.LfasrClientImp;
import com.iflytek.msp.cpdb.lfasr.exception.LfasrException;
import com.iflytek.msp.cpdb.lfasr.model.LfasrType;
import com.iflytek.msp.cpdb.lfasr.model.Message;
import com.iflytek.msp.cpdb.lfasr.model.ProgressStatus;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.PropertyConfigurator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

/**
 * Created by bingbing on 2018/5/3.
 */
@Controller
@RequestMapping(value = "/")
public class MainServlet {

    // 原始音频存放地址
    private static final String local_file = "...";
    /*
     * 转写类型选择：标准版和电话版分别为：
//     * LfasrType.LFASR_STANDARD_RECORDED_AUDIO 和 LfasrType.LFASR_TELEPHONY_RECORDED_AUDIO
     * */
//    private static final LfasrType type = LfasrType.LFASR_STANDARD_RECORDED_AUDIO;
    private static final LfasrType type = LfasrType.LFASR_TELEPHONY_RECORDED_AUDIO;
    // 等待时长（秒）
    private static int sleepSecond = 20;

    private static String contextFileName = "带时间戳版.txt";
    private static String taskFileName = "task.txt";

    @Resource(name = "taskExecutor")
    private TaskExecutor taskExecutor;


    @RequestMapping(value = "/do")
    public void doXF(@RequestParam("file") List<MultipartFile> file, HttpServletRequest req, HttpServletResponse rsp) throws Exception {
        for (int i = 0; i < file.size(); i++) {

            MultipartFile mFile = file.get(i);

            Properties properties = new Properties();
            // 使用ClassLoader加载properties配置文件生成对应的输入流
            InputStream in = MainServlet.class.getClassLoader().getResourceAsStream("config.properties");
            // 使用properties对象加载输入流
            properties.load(in);

            File fileDir = new File(properties.getProperty("file_path"));

            System.out.println(fileDir.getPath());

            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }

            File taskFileDir = new File(fileDir.getPath(), mFile.getOriginalFilename().split("\\.")[0]);
            if (!taskFileDir.exists()) {
                taskFileDir.mkdirs();
            }


            File toFile = new File(taskFileDir, mFile.getOriginalFilename());
            if (toFile.exists()) {
                toFile.delete();
            }


            File taskFile = new File(taskFileDir, taskFileName);

            FileUtils.writeByteArrayToFile(toFile, mFile.getBytes());

            taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    // 加载配置文件
                    PropertyConfigurator.configure(getClass().getResource("/").getPath() + "log4j.properties");

                    File directory = new File("");// 参数为空
                    String courseFile = "";
                    try {
                        courseFile = directory.getCanonicalPath();
                        System.out.println(courseFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // 初始化LFASR实例
                    LfasrClientImp lc = null;
                    try {
                        lc = LfasrClientImp.initLfasrClient();
                    } catch (LfasrException e) {
                        // 初始化异常，解析异常描述信息
                        Message initMsg = JSON.parseObject(e.getMessage(), Message.class);
                        System.out.println("ecode=" + initMsg.getErr_no());
                        System.out.println("failed=" + initMsg.getFailed());
                    }


                    // 获取上传任务ID
                    String task_id = "";
                    if (!taskFile.exists()) {
                        HashMap<String, String> params = new HashMap<>();
                        params.put("has_participle", "false");
                        try {
                            // 上传音频文件
                            Message uploadMsg = lc.lfasrUpload(toFile.getPath(), type, params);

                            // 判断返回值
                            int ok = uploadMsg.getOk();
                            if (ok == 0) {
                                // 创建任务成功
                                task_id = uploadMsg.getData();

                                File taskFile = new File(taskFileDir, taskFileName);
                                writeToFile(taskFile.getPath(), task_id);

                                System.out.println("task_id=" + task_id);
                            } else {
                                // 创建任务失败-服务端异常
                                System.out.println("ecode=" + uploadMsg.getErr_no());
                                System.out.println("failed=" + uploadMsg.getFailed());
                            }
                        } catch (LfasrException e) {
                            // 上传异常，解析异常描述信息
                            Message uploadMsg = JSON.parseObject(e.getMessage(), Message.class);
                            System.out.println("ecode=" + uploadMsg.getErr_no());
                            System.out.println("failed=" + uploadMsg.getFailed());
                        }
                    } else {
                        task_id = readFile(taskFile);
                    }

                    try {
                        toFile.delete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    boolean first = true;

                    // 循环等待音频处理结果
                    while (true) {
                        if (!first)
                            try {
                                // 睡眠1min。另外一个方案是让用户尝试多次获取，第一次假设等1分钟，获取成功后break；失败的话增加到2分钟再获取，获取成功后break；再失败的话加到4分钟；8分钟；??
                                Thread.sleep(sleepSecond * 1000);
                                System.out.println("waiting ...");
                            } catch (InterruptedException e) {
                            }

                        first = false;
                        try {
                            // 获取处理进度
                            Message progressMsg = lc.lfasrGetProgress(task_id);

                            // 如果返回状态不等于0，则任务失败
                            if (progressMsg.getOk() != 0) {
                                System.out.println("task was fail. task_id:" + task_id);
                                System.out.println("ecode=" + progressMsg.getErr_no());
                                System.out.println("failed=" + progressMsg.getFailed());

                                // 服务端处理异常-服务端内部有重试机制（不排查极端无法恢复的任务）
                                // 客户端可根据实际情况选择：
                                // 1. 客户端循环重试获取进度
                                // 2. 退出程序，反馈问题
                                break;
                            } else {
                                ProgressStatus progressStatus = JSON.parseObject(progressMsg.getData(), ProgressStatus.class);
                                if (progressStatus.getStatus() == 9) {
                                    // 处理完成
                                    System.out.println("task was completed. task_id:" + task_id);
                                    break;
                                } else {
                                    // 未处理完成
                                    System.out.println("task was incomplete. task_id:" + task_id + ", status:" + progressStatus.getDesc());
                                    continue;
                                }
                            }
                        } catch (LfasrException e) {
                            // 获取进度异常处理，根据返回信息排查问题后，再次进行获取
                            Message progressMsg = JSON.parseObject(e.getMessage(), Message.class);
                            System.out.println("ecode=" + progressMsg.getErr_no());
                            System.out.println("failed=" + progressMsg.getFailed());
                        }

                    }

                    // 获取任务结果
                    try {
                        Message resultMsg = lc.lfasrGetResult(task_id);
                        // 如果返回状态等于0，则任务处理成功
                        if (resultMsg.getOk() == 0) {
                            // 打印转写结果
                            System.out.println(resultMsg.getData());

                            JSONArray array = JSONArray.parseArray(resultMsg.getData());
                            StringBuilder builder = new StringBuilder();
                            StringBuilder builder1 = new StringBuilder();
                            String speaker = "";
                            for (int i = 0; i < array.size(); i++) {
                                JSONObject obj = array.getJSONObject(i);
                                String startTime = turnTime(obj.getInteger("bg") / 1000);
                                String endTime = turnTime(obj.getInteger("ed") / 1000);
                                String text = obj.getString("onebest");
                                if (type == LfasrType.LFASR_TELEPHONY_RECORDED_AUDIO) {
                                    String speaker1 = obj.getString("speaker");
                                    builder1.append((!speaker1.equals(speaker) ? (i > 0 ? "\n" : "") + speaker1 + "\t" : "") + text);
                                    speaker = speaker1;
                                } else {
                                    builder1.append(text);
                                }

                            }
//                        writeToFile(new File(taskFileDir, contextFileName).getPath(), builder.toString());
                            writeToFile(new File(taskFileDir, taskFileDir.getName() + ".txt").getPath(), builder1.toString());
                        } else {
                            // 转写失败，根据失败信息进行处理
                            System.out.println("ecode=" + resultMsg.getErr_no());
                            System.out.println("failed=" + resultMsg.getFailed());
                        }
                    } catch (LfasrException e) {
                        // 获取结果异常处理，解析异常描述信息
                        Message resultMsg = JSON.parseObject(e.getMessage(), Message.class);
                        System.out.println("ecode=" + resultMsg.getErr_no());
                        System.out.println("failed=" + resultMsg.getFailed());
                    }
                }
            });
        }

        rsp.sendRedirect("history");
    }

    @RequestMapping(value = "/history")
    public ModelAndView history(HttpServletRequest req) throws Exception {
        Properties properties = new Properties();
        // 使用ClassLoader加载properties配置文件生成对应的输入流
        InputStream in = MainServlet.class.getClassLoader().getResourceAsStream("config.properties");
        // 使用properties对象加载输入流
        properties.load(in);

        File fileDir = new File(properties.getProperty("file_path"));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        List<String> files = orderByDate(fileDir.getPath());
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        for (int i = 0; i < files.size(); i++) {
            if (new File(fileDir, files.get(i) + "/" + files.get(i) + ".txt").exists()) {
                map.put(files.get(i), true);
            } else {
                map.put(files.get(i), false);
            }
        }

        return new ModelAndView("history").addObject("files", map);
    }

    @RequestMapping(value = "/download")
    public ResponseEntity download(@RequestParam String file, HttpServletRequest req) throws Exception {
        Properties properties = new Properties();
        // 使用ClassLoader加载properties配置文件生成对应的输入流
        InputStream in = MainServlet.class.getClassLoader().getResourceAsStream("config.properties");
        // 使用properties对象加载输入流
        properties.load(in);

        File fileDir = new File(properties.getProperty("file_path"));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        File mFile = new File(fileDir, file + "/" + file + ".txt");
        HttpHeaders headers = new HttpHeaders();
        String fileName = new String((file + ".txt").getBytes("UTF-8"), "iso-8859-1");//为了解决中文名称乱码问题
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(FileUtils.readFileToByteArray(mFile),
                headers, HttpStatus.CREATED);
    }

    //按日期排序
    public static List<String> orderByDate(String fliePath) {
        File file = new File(fliePath);
        File[] fs = file.listFiles();
        Arrays.sort(fs, new Comparator<File>() {
            public int compare(File f1, File f2) {

                File f1Task = new File(f1.getPath(), taskFileName);
                File f2Task = new File(f2.getPath(), taskFileName);
                if (!f1Task.exists()) {
                    f1Task = f1;
                }
                if (!f2Task.exists()) {
                    f2Task = f2;
                }

                long diff = f2Task.lastModified() - f1Task.lastModified();
                if (diff > 0)
                    return 1;
                else if (diff == 0)
                    return 0;
                else
                    return -1;
            }

            public boolean equals(Object obj) {
                return true;
            }

        });

        List<String> files = new ArrayList<>();
        for (int i = 0; i < fs.length; i++) {
            if (fs[i].isDirectory()) {
                files.add(fs[i].getName());
            }
        }
        return files;
    }

    public static String readFile(File file) {
        String charset = "UTF-8";

        if (file.isFile() && file.exists()) {
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, charset);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                StringBuffer sb = new StringBuffer();
                String text = null;
                while ((text = bufferedReader.readLine()) != null) {
                    sb.append(text);
                }
                return sb.toString();
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
        return null;
    }


    public static void writeToFile(String fileName, String content) {
        File file = new File(fileName);

        try {
            if (file.exists()) {
                file.createNewFile();
            }
            OutputStreamWriter oStreamWriter = new OutputStreamWriter(new FileOutputStream(file), "utf-8");
            oStreamWriter.append(content);
            oStreamWriter.flush();
            oStreamWriter.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 秒转为分:秒
     *
     * @param second
     * @return
     */
    public static String turnTime(int second) {
        int d = 0;
        int s = 0;
        int temp = second % 3600;
        if (second > 3600) {
            if (temp != 0) {
                if (temp > 60) {
                    d = temp / 60;
                    if (temp % 60 != 0) {
                        s = temp % 60;
                    }
                } else {
                    s = temp;
                }
            }
        } else {
            d = second / 60;
            if (second % 60 != 0) {
                s = second % 60;
            }
        }
        return (d > 0 ? d > 9 ? d : "0" + d : "00") + ":" + (s > 9 ? s : "0" + s);
    }
}
