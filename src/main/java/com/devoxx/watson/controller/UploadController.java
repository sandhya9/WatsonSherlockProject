package com.devoxx.watson.controller;

import com.devoxx.watson.configuration.DevoxxWatsonInitializer;
import com.devoxx.watson.model.Article;
import com.devoxx.watson.model.FileBucket;
import com.devoxx.watson.util.UploadValidator;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

@Controller
class UploadController {

    private static final Logger LOGGER = Logger.getLogger(UploadController.class.getName());

    @Autowired
    UploadValidator uploadValidator;

    @Autowired
    ConceptInsightsService conceptInsightsService;

    @InitBinder
    protected void initBinderFileBucket(WebDataBinder binder) {
        binder.setValidator(uploadValidator);
    }

    @RequestMapping(value = {"/", "/welcome"}, method = RequestMethod.GET)
    public String getHomePage() {
        return "welcome";
    }

    @RequestMapping(value = "/audioFileUploader", method = RequestMethod.GET)
    public String getSingleUploadPage(ModelMap model) {
        FileBucket fileModel = new FileBucket();
        model.addAttribute("fileBucket", fileModel);
        return "audioFileUploader";
    }

    @RequestMapping(value = "/audioFileUploader", method = RequestMethod.POST)
    public String audioFileUpload(@Valid FileBucket fileBucket, BindingResult result, ModelMap model) throws IOException {

        if (result.hasErrors()) {
            LOGGER.log(Level.FINE, "Form validation errors");
            return "audioFileUploader";
        } else {
            LOGGER.log(Level.FINE, "Fetching audio file");
            MultipartFile multipartFile = fileBucket.getFile();

            File uploadPath = new File(DevoxxWatsonInitializer.LOCATION);

            // Now do something with file...
            File tempFile = File.createTempFile("devoxx",
                    fileBucket.getFile().getOriginalFilename(),
                    uploadPath);

            FileCopyUtils.copy(fileBucket.getFile().getBytes(), tempFile);
            String fileName = multipartFile.getOriginalFilename();
            model.addAttribute("content", fileName);

            // Create the meta data txt file with YouTube link
            String txtFileName = tempFile.getAbsolutePath()+".txt";
            File txtFile = new File(txtFileName);
            PrintWriter writer = new PrintWriter(txtFile, "UTF-8");
            writer.println(fileBucket.getLink());
            writer.println(fileBucket.getDocName());
            writer.close();

            return "success";
        }
    }


    @RequestMapping(value = "/articleUploader", method = RequestMethod.GET)
    public String getArticlePage(ModelMap model) {
        final Article article = new Article();
        model.addAttribute("article", article);
        return "articleUploader";
    }


    @RequestMapping(value = "/articleUploader", method = RequestMethod.POST)
    public String contentUpload(@Valid Article article, BindingResult result, ModelMap model) {

        if (result.hasErrors()) {
            LOGGER.log(Level.FINE, "Form validation errors");
            return "articleUploader";

        } else {

            LOGGER.info("Processing " + article.getLink());

            try {
                String ampLink = article.getLink();
                if (!ampLink.endsWith("?amp")) {
                    ampLink = article.getLink() + "?amp";
                }


                final Connection connect = Jsoup.connect(ampLink);
                Document doc = connect.get();

                if (doc != null) {
                    String title = doc.title();

                    if (!conceptInsightsService.documentExists(title)) {

                        final Elements select = doc.select("div.amp-wp-content p");

                        conceptInsightsService.createDocument(title, article.getLink(), select.text());
                    }

                    model.addAttribute("content", doc.title());
                } else {
                    result.addError(new ObjectError("link", "Try again"));
                    return "articleUploader";
                }

            } catch (IOException | IllegalArgumentException e) {
                result.addError(new ObjectError("link", "Wrong URL"));
                return "articleUploader";
            }

        }

        return "success";
    }
}