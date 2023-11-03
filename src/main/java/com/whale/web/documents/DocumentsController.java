package com.whale.web.documents;

import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.whale.web.documents.certificategenerator.model.CertificateGeneratorForm;
import com.whale.web.documents.compactconverter.model.CompactConverterForm;
import com.whale.web.documents.qrcodegenerator.model.QRCodeEmail;
import com.whale.web.documents.qrcodegenerator.model.QRCodeLink;
import com.whale.web.documents.qrcodegenerator.model.QRCodeWhatsapp;
import com.whale.web.documents.qrcodegenerator.service.QRCodeEmailService;
import com.whale.web.documents.qrcodegenerator.service.QRCodeWhatsappService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.whale.web.documents.certificategenerator.service.CreateCertificateService;
import com.whale.web.documents.certificategenerator.service.ProcessWorksheetService;
import com.whale.web.documents.compactconverter.service.CompactConverterService;
import com.whale.web.documents.filecompressor.FileCompressorService;
import com.whale.web.documents.imageconverter.model.ImageConversionForm;
import com.whale.web.documents.imageconverter.service.ImageConverterService;
import com.whale.web.documents.qrcodegenerator.service.QRCodeLinkService;

@RestController
@RequestMapping(value = "api/v1/documents")
@Tag(name = "API for documents resource palette")
public class DocumentsController {

    @Autowired
    CompactConverterService compactConverterService;

    @Autowired
    FileCompressorService fileCompressorService;
    
    @Autowired
    ImageConverterService imageConverterService;
    
    @Autowired
    QRCodeLinkService qrCodeLinkService;

	@Autowired
	QRCodeWhatsappService qrCodeWhatsappService;

	@Autowired
	QRCodeEmailService qrCodeEmailService;

    @Autowired
    ProcessWorksheetService processWorksheetService;
    
    @Autowired
    CreateCertificateService createCertificateService;

	private static final Logger logger = LoggerFactory.getLogger(DocumentsController.class);
	private static final String ATTACHMENT_FILENAME = "attachment; filename=";



	@PostMapping(value = "/compactconverter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Compact Converter", description = "Convert ZIP to other compression formats", method = "POST")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success", content = {@Content(mediaType = "application/octet-stream")}),
			@ApiResponse(responseCode = "500", description = "INTERNAL_SERVER_ERROR")
	})
	public ResponseEntity<byte[]> compactConverter(CompactConverterForm form, @RequestPart List<MultipartFile> files) {
		try {
			List<byte[]> filesConverted = compactConverterService.converterFile(files, form.getAction());
			String convertedFileName =  StringUtils.stripFilenameExtension(Objects.requireNonNull(files.get(0).getOriginalFilename()))
										+ form.getAction().toLowerCase();

			byte[] responseBytes;
			String contentType = MediaType.APPLICATION_OCTET_STREAM.toString();

			if (filesConverted.size() == 1) {
				responseBytes = filesConverted.get(0);
			} else {
				responseBytes = createZipArchive(filesConverted);
				contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
			}

			logger.info("File compressed successfully");
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType(contentType))
					.header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + convertedFileName)
					.header(CacheControl.noCache().toString())
					.body(responseBytes);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
    }

	private byte[] createZipArchive(List<byte[]> files) throws IOException {
		ByteArrayOutputStream zipStream = new ByteArrayOutputStream();
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(zipStream)) {
			for (int i = 0; i < files.size(); i++) {
				byte[] fileBytes = files.get(i);
				ZipEntry zipEntry = new ZipEntry("file" + (i + 1) + ".zip");
				zipOutputStream.putNextEntry(zipEntry);
				zipOutputStream.write(fileBytes);
				zipOutputStream.closeEntry();
			}
		}
		return zipStream.toByteArray();
	}



	@PostMapping(value = "/filecompressor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "File Compressor", description = "Compresses one or more files.", method = "POST")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success", content = {	@Content(mediaType = "application/zip") }),
			@ApiResponse(responseCode = "500", description = "Error compressing file")
	})
    public ResponseEntity<byte[]> fileCompressor(@RequestPart List<MultipartFile> file) {
		
            try {
                byte[] bytes = fileCompressorService.compressFiles(file);

				return ResponseEntity.ok()
						.contentType(MediaType.APPLICATION_OCTET_STREAM)
						.header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME+"compressedFile.zip")
						.header(CacheControl.noCache().toString())
						.body(bytes);

			} catch (Exception e) {
				logger.error(e.getMessage());
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
			}
    }



	@PostMapping(value = "/imageconverter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Image Converter", description = "Convert an image to another format", method = "POST")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success", content = { @Content(mediaType = "application/octet-stream")}),
			@ApiResponse(responseCode = "500", description = "Error converting image")
	})
	public ResponseEntity<byte[]> imageConverter(@RequestPart MultipartFile image, ImageConversionForm imageConversionForm) {
		try {
			byte[] bytes = imageConverterService.convertImageFormat(imageConversionForm.getOutputFormat(), image);

			String originalFileNameWithoutExtension = StringUtils.stripFilenameExtension(Objects.requireNonNull(image.getOriginalFilename()));
			String convertedFileName = originalFileNameWithoutExtension + "." + imageConversionForm.getOutputFormat().toLowerCase();

			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("image/" + imageConversionForm.getOutputFormat().toLowerCase()))
					.header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + convertedFileName)
					.header(CacheControl.noCache().toString())
					.body(bytes);

		} catch (Exception e) {
			logger.error(e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}

	}



	@PostMapping("/qrcodegenerator/link")
	@Operation(summary = "QRCOde Generator for link", description = "Generates QRCode for Link in the chosen color", method = "POST")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success", content = { @Content(mediaType = "image/png")}),
			@ApiResponse(responseCode = "500", description = "Error generating qrcode")
	})
	public ResponseEntity<byte[]> qrCodeGeneratorLink(QRCodeLink qrCodeLink){
		try {
			byte[] bytes = qrCodeLinkService.generateQRCode(qrCodeLink.getLink(), qrCodeLink.getPixelColor());

			return ResponseEntity.ok()
					.contentType(MediaType.IMAGE_PNG)
					.header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + "QRCodeLink.png")
					.header(CacheControl.noCache().toString())
					.body(bytes);

		} catch (Exception e) {
			logger.error(e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}



	@PostMapping("/qrcodegenerator/email")
	@Operation(summary = "QRCOde Generator for email", description = "Generates QRCode for Email in the chosen color", method = "POST")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success", content = { @Content(mediaType = "image/png")}),
			@ApiResponse(responseCode = "500", description = "Error generating qrcode")
	})
	public ResponseEntity<byte[]> qrCodeGeneratorEmail(QRCodeEmail qrCodeEmail){

		try {
			byte[] bytes = qrCodeEmailService
					.generateEmailLinkQRCode(qrCodeEmail.getEmail(), qrCodeEmail.getTitleEmail(), qrCodeEmail.getTextEmail(), qrCodeEmail.getPixelColor());

			return ResponseEntity.ok()
					.contentType(MediaType.IMAGE_PNG)
					.header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + "QRCodeEmail.png")
					.header(CacheControl.noCache().toString())
					.body(bytes);

		} catch (Exception e) {
			logger.error(e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}



	@PostMapping("/qrcodegenerator/whatsapp")
	@Operation(summary = "QRCOde Generator for whatsapp", description = "Generates QRCode for WhatsApp in the chosen color",  method = "POST")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success", content = { @Content(mediaType = "image/png")}),
			@ApiResponse(responseCode = "500", description = "Error generating qrcode")
	})
	public ResponseEntity<byte[]> qrCodeGeneratorWhatsapp(QRCodeWhatsapp qrCodeWhatsapp){

		try {
			byte[] bytes = qrCodeWhatsappService
					.generateWhatsAppLinkQRCode(qrCodeWhatsapp.getPhoneNumber(), qrCodeWhatsapp.getText(), qrCodeWhatsapp.getPixelColor());

			return ResponseEntity.ok()
					.contentType(MediaType.IMAGE_PNG)
					.header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + "QRCodeWhatsapp.png")
					.header(CacheControl.noCache().toString())
					.body(bytes);

		} catch (Exception e) {
			logger.error(e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}



	@PostMapping("/certificategenerator")
	@Operation(summary = "Certificate Generator", description = "Generates certificates with a chosen layout", method = "POST")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success", content = { @Content(mediaType = "application/octet-stream")}),
			@ApiResponse(responseCode = "500", description = "Error generating certificate")
	})
	public ResponseEntity<byte[]> certificateGenerator(CertificateGeneratorForm certificateGeneratorForm){
		try {
		    List<String> names = processWorksheetService.savingNamesInAList(certificateGeneratorForm.getWorksheet().getWorksheet());
		    byte[] bytes = createCertificateService.createCertificates(certificateGeneratorForm.getCertificate(), names);

			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_OCTET_STREAM)
					.header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + "certificates.zip")
					.body(bytes);

		} catch (Exception e) {
			logger.error(e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

}
