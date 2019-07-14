package angel.hack.transcribeapp;

import java.io.IOException;
import java.io.*;
import java.util.stream.Collectors;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.AbstractHandler;


// AWS SDK jar: https://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk/1.11.592
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import com.amazonaws.services.transcribe.*;
import com.amazonaws.services.transcribe.model.*;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.GetObjectRequest;

import com.fasterxml.jackson.databind.*;


public class App extends AbstractHandler
{
    final String bucketName = "testing434224"; // unique s3 bucket name
    final String outputBucketName = bucketName + "output"; // unique s3 bucket name

    void processTranscribedTest(String s3Key) {
        String fileObjKeyName = "key" + System.currentTimeMillis();

        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1)
                    .withCredentials(new ProfileCredentialsProvider())
                    .build();

            GetObjectRequest getObjectRequest = new GetObjectRequest(outputBucketName, s3Key);
            S3Object obj = s3Client.getObject(getObjectRequest);
            String result = new BufferedReader(new InputStreamReader(obj.getObjectContent())).lines().collect(Collectors.joining(""));
            System.out.println("got it! " + result);

            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode root = mapper.readTree(result);
                JsonNode value = root.get("results").get("transcripts").get(0).get("transcript");
                System.out.println("value: " + Arrays.asList(value.textValue().split("\\s")));
            } catch (Exception e) {
                System.out.println("e! " + e);
            }
//            hi
        }
        catch(AmazonServiceException e) {
            e.printStackTrace();
        }
        catch(SdkClientException e) {
            e.printStackTrace();
        }
    }

    void transcribe(String keyName) {
        final String jobName = "JobName" + keyName;
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        System.out.println("Sending request...");
        StartTranscriptionJobResult result = 
            AmazonTranscribeClientBuilder.standard()
                .withRegion(Regions.US_EAST_1).build()
                .startTranscriptionJob(
                    startTranscriptionJobRequest
                        .withMedia(new Media().withMediaFileUri("https://s3-us-east-1.amazonaws.com/" + bucketName + "/" + keyName))
                        .withMediaFormat(MediaFormat.Wav)
                        .withLanguageCode(LanguageCode.EnUS)
                        .withTranscriptionJobName(jobName)
                        .withOutputBucketName(outputBucketName)
                );
        System.out.println("Result: " + result.getTranscriptionJob());

        new Thread(
            () -> {
                GetTranscriptionJobResult result2 = null;
                do {
                    try {
                        result2 = AmazonTranscribeClientBuilder.standard().withRegion(Regions.US_EAST_1).build()
                                .getTranscriptionJob(new GetTranscriptionJobRequest().withTranscriptionJobName("JobName" + keyName));
                        System.out.println("Result: " + result2.getTranscriptionJob());
                        Thread.sleep(10 * 1000);
                    } catch (Exception e) {
                        //
                    }
                } while (result2.getTranscriptionJob().getTranscriptionJobStatus().equals(TranscriptionJobStatus.IN_PROGRESS.toString()));
                System.out.println("Result: " + result2.getTranscriptionJob());
                processTranscribedTest(jobName + ".json");
            }
        ).start();
    }


    // https://docs.aws.amazon.com/AmazonS3/latest/dev/UploadObjSingleOpJava.html
    String uploadToS3(File file) throws IOException {
        // String clientRegion = "*** Client region ***";
        // String stringObjKeyName = "*** String object key name ***";
        String fileObjKeyName = "key" + System.currentTimeMillis();
        // String fileName = "output" + System.currentTimeMilis() + ".wav";

        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1)
                    .withCredentials(new ProfileCredentialsProvider())
                    .build();
        
            // Upload a text string as a new object.
            // s3Client.putObject(bucketName, stringObjKeyName, "Uploaded String Object");
            
            // Upload a file as a new object with ContentType and title specified.
            PutObjectRequest request = new PutObjectRequest(bucketName, fileObjKeyName, file);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("plain/text");
            // metadata.addUserMetadata("x-amz-meta-title", "someTitle");
            request.setMetadata(metadata);
            s3Client.putObject(request);
        }
        catch(AmazonServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process it, so it returned an error response.
            e.printStackTrace();
        }
        catch(SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client couldn't parse the response from Amazon S3.
            e.printStackTrace();
        }
        return fileObjKeyName;
    }

    private void writeAndConvert(InputStream is) throws IOException {
        // https://docs.oracle.com/javaee/7/api/javax/servlet/http/Part.html
        byte[] buffer = new byte[1024 * 1024];
        int numRead = is.read(buffer);
        System.out.println("numRead " + numRead);
        File targetFile = new File("..\\output.webm");
        OutputStream outStream = new FileOutputStream(targetFile);
        outStream.write(buffer);
        outStream.close();
        System.out.println("Written to: " + targetFile);

        try {
            File startDir = new File("..\\ffmpeg-20190712-81d3d7d-win64-static\\bin");

            // Need to delete old file first
            try {
                new File("..\\output.wav").delete();
            } catch(Exception e) {
            }

            ProcessBuilder pb = new ProcessBuilder(
                "C:\\Users\\Josh\\Desktop\\projects\\angel_hack\\ffmpeg-20190712-81d3d7d-win64-static\\bin\\ffmpeg.exe", 
                "-i", 
                "..\\..\\output.webm", 
                "..\\..\\output.wav").directory(startDir).inheritIO();
            System.out.println("Command? " + pb.command() + ", " + pb.directory());
            Process p = pb.start();
            // new Thread(new Runnable() {
            //     public void run() {
            //         String result = new BufferedReader(new InputStreamReader(p.getInputStream())).lines().collect(Collectors.joining("\n"));
            //         System.out.println("Output: " + result);
            //     }
            // }).start();

            // new Thread(new Runnable() {
            //     public void run() {
            //         String result = new BufferedReader(new InputStreamReader(p.getErrorStream())).lines().collect(Collectors.joining("\n"));
            //         System.out.println("Error: " + result);
            //     }
            // }).start();

            int exitCode = p.waitFor();
            System.out.println("Conversion exit code: " + exitCode);
            final String keyName = uploadToS3(new File("..\\output.wav"));
            transcribe(keyName);
        } catch (Exception e) {
            System.out.println("Error converting: " + e);
        }
    }

    @Override
    public void handle( String target,
                        Request baseRequest,
                        HttpServletRequest request,
                        HttpServletResponse response ) throws IOException,
                                                      ServletException
    {
        // https://www.eclipse.org/lists/jetty-users/msg03294.html
        baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, new javax.servlet.MultipartConfigElement(System.getProperty("java.io.tmpdir")));
        // https://docs.oracle.com/javaee/7/api/javax/servlet/http/Part.html
        // System.out.println("data? " + request.getPart("data").getInputStream());
        InputStream is = request.getPart("data").getInputStream();
        writeAndConvert(is);

        // .\ffmpeg-20190712-81d3d7d-win64-static\bin\ffmpeg -i output.webm output.wav

        // Declare response encoding and types
        response.setContentType("text/html; charset=utf-8");

        // Declare response status code
        response.setStatus(HttpServletResponse.SC_OK);

        // Write back response
        response.getWriter().println("<h1>Hello World</h1>");

        // Inform jetty that this request has now been handled
        baseRequest.setHandled(true);
    }

    public static void main(String[] args) throws Exception
    {
        Server server = new Server(8080);
        server.setHandler(new App());

        server.start();
        server.join();
    }
}
