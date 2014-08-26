/*
 * 
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.magnum.dataup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.http.HTTPBinding;

import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.model.VideoStatus.VideoState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import retrofit.client.Response;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Streaming;

@Controller
public class VideoSvcController {

	/**
	 * You will need to create one or more Spring controllers to fulfill the
	 * requirements of the assignment. If you use this file, please rename it
	 * to something other than "AnEmptyController"
	 * 
	 * 
		 ________  ________  ________  ________          ___       ___  ___  ________  ___  __       
		|\   ____\|\   __  \|\   __  \|\   ___ \        |\  \     |\  \|\  \|\   ____\|\  \|\  \     
		\ \  \___|\ \  \|\  \ \  \|\  \ \  \_|\ \       \ \  \    \ \  \\\  \ \  \___|\ \  \/  /|_   
		 \ \  \  __\ \  \\\  \ \  \\\  \ \  \ \\ \       \ \  \    \ \  \\\  \ \  \    \ \   ___  \  
		  \ \  \|\  \ \  \\\  \ \  \\\  \ \  \_\\ \       \ \  \____\ \  \\\  \ \  \____\ \  \\ \  \ 
		   \ \_______\ \_______\ \_______\ \_______\       \ \_______\ \_______\ \_______\ \__\\ \__\
		    \|_______|\|_______|\|_______|\|_______|        \|_______|\|_______|\|_______|\|__| \|__|
                                                                                                                                                                                                                                                                        
	 * 
	 */
	
	@RequestMapping(value = VideoSvcApi.VIDEO_SVC_PATH, method = RequestMethod.GET)
	public @ResponseBody Collection<Video> getVideoList() {
		
		Collection videoList = this.videos.values();
		return videoList;
		
	} // end getVideoList
	
	@RequestMapping(value = VideoSvcApi.VIDEO_SVC_PATH, method = RequestMethod.POST)
	public	@ResponseBody Video addVideo(
			@RequestBody Video video) {

		System.out.println("addVideo, title: '" + video.getTitle() + "'.");
		this.store(video);
		
		String url = this.getDataUrl(video.getId());
		video.setDataUrl(url);

		System.out.println("addVideo, id: " + video.getId()
				+ ", title: '" + video.getTitle()
				+ "', url: '" + video.getDataUrl() + "'.");
		
		return video;
		
	} // end addVideo
	
	@RequestMapping(VideoSvcApi.VIDEO_DATA_PATH)
	public	@ResponseBody VideoStatus setVideoData(
			
			@PathVariable(VideoSvcApi.ID_PARAMETER) long id,
			@RequestPart(VideoSvcApi.DATA_PARAMETER)  MultipartFile videoData) {
		
		System.out.println("setVideoData, id: " + id + ".");
		Video video = this.videos.get(id);
		if (video == null) {
			
			throw new ResourceNotFoundException();
			
		} // end if (video == null)
		
		//
		try {
			
			VideoFileManager videoFileManager = VideoFileManager.get();
			InputStream videoInputeStream = videoData.getInputStream();
			videoFileManager.saveVideoData(video, videoInputeStream);
			
		} catch (IOException ioe) {
			ioe.printStackTrace();
			
		}
		
		return new VideoStatus(VideoState.READY);
		
	} // end setVideoData

	@RequestMapping(value = VideoSvcApi.VIDEO_DATA_PATH, method = RequestMethod.GET)
	public HttpServletResponse getData(
			@PathVariable(VideoSvcApi.ID_PARAMETER) long id,
			HttpServletResponse response) {
		
		System.out.println("getData, id: " + id + ".");
		Video video = this.videos.get(id);
		if (video == null) {
			
			System.out.println("Video not found, id: " + id
					+ "! Return BAD_REQUEST (" + HttpStatus.BAD_REQUEST.value() + ").");
			try {
				response.sendError(HttpStatus.NOT_FOUND.value());
			
			} catch (IOException ioe) {
				ioe.printStackTrace();
				
			} // end catch (IOException ioe)
			
			return response;
			
		} // end if (video == null)
		else {
			
			System.out.println("Video found, id: " + video.getId()
					+ ", title: '" + video.getTitle() +  "'.");
			
			try {

				
				VideoFileManager videoFileManager = VideoFileManager.get();
				OutputStream outputStream = response.getOutputStream();
				
				System.out.println("Video id: " + video.getId()
						+ ", copy video data...");
				
				videoFileManager.copyVideoData(video, outputStream);
				
				System.out.println("Video id: " + video.getId()
						+ ", copy video data terminated.");
				
			} catch (IOException ioe) {
				
				ioe.printStackTrace();
				response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
				
			} // end catch (IOException ioe)
			finally {
				
				return response;
				
			} // end finally 
			
		} // end else if (video == null)

	} // end getData
	
	@ResponseStatus(value = org.springframework.http.HttpStatus.NOT_FOUND)
 	public final class ResourceNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1633882937487161588L;
		
 	} // end ResourceNotFoundException
	
	/**
	 * 
	 * @param videoId
	 * @return
	 */
	private String getDataUrl(long videoId) {
		
		String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
		return url;
				
	} // end getDataUrl
	
	/**
	 * 
	 * @return
	 */
	private String getUrlBaseForLocalServer() {
		
		HttpServletRequest request =
				((ServletRequestAttributes)RequestContextHolder.getRequestAttributes())
				.getRequest();
		
		String base = "http://" + request.getServerName()
				+ ((request.getServerPort() != 80) ? ":" + request.getServerPort(): "");
				
		return base;
		
	} // end getUrlBaseForLocalServer
	
	private static final AtomicLong currentId = new AtomicLong(0L);
	
	private Map<Long, Video> videos = new HashMap<Long, Video>();
	
	/**
	 * 
	 * @param video
	 * @return
	 */
	public Video store(Video video) {
		
		checkAndSetId(video);
		this.videos.put(video.getId(), video);
		
		return video;
		
	} // end store
	
	/**
	 * 
	 * @param entity
	 */
	private void checkAndSetId(Video entity) {
		
		if (entity.getId() == 0)
			entity.setId(VideoSvcController.currentId.incrementAndGet());
		
		return;
		
	} // end checkAndSetId
	
} // end VideoSvcController
