require 'rho/rhocontroller'
require 'helpers/application_helper'

class FreightController < Rho::RhoController

  include ApplicationHelper

  #GET /Freight
  def index
    @freights = Freight.find(:all)
        unless $sync_in_progress
         $sync_in_progress = true
         SyncEngine.dosync
        end
        render
  end

  # GET /Freight/{1}
  def show
    @freight = Freight.find(@params['id'])
    render :action => :show
  end

  # GET /Freight/new
  def new
    @freight = Freight.new
    render :action => :new
  end

  # GET /Freight/{1}/edit
  def edit
    @freight = Freight.find(@params['id'])
    render :action => :edit
  end

  # POST /Freight/create
  def create

  end
  
  def search
    Freight.search(
      :from => 'search',
      :search_params => { :origin_name_like => @params['origin'], :destination_name_like => @params['destination'], :weight_greater_than => @params['weight'] },
      :offset => 0,
      :max_results => 50,
      :callback => '/app/Freight/search_callback',
      :callback_param => "" )
      
      render :action => :wait
  end
  
  def search_callback
    logger.debug "Entrando a search callback, status #{status.inspect}"
    if (status && status == 'ok')
      WebView.navigate ( url_for :action => :show_page )
    end
    #TODO: show error page if status == 'error'
    render :action => :ok
  end
  
  def show_page
    @freights = Freight.find(:all)    
    render :action => :show_page
  end

  # POST /Freight/{1}/update
  def update
    @freight = Freight.find(@params['id'])
    @freight.update_attributes(@params['freight'])
    redirect :action => :index
  end

  # POST /Freight/{1}/delete
  def delete
    @freight = Freight.find(@params['id'])
    @freight.destroy
    redirect :action => :index
  end
end
