require 'rho/rhocontroller'
require 'helpers/application_helper'

class FreightController < Rho::RhoController

  include ApplicationHelper

  def index
    @freights = Freight.find(:all)
        unless $sync_in_progress
         $sync_in_progress = true
         SyncEngine.dosync
        end
        render
  end

  def show
    @freight = Freight.find(@params['id'])
    # puts "En el show de freight"
    # @contact = Contact.find(:first, :conditions => ["offer_id = ?", @freight.object.delete("{}")])
    render :action => :show
  end

  def new
    @freight = Freight.new
    render :action => :new
  end

  def edit
    @freight = Freight.find(@params['id'])
    render :action => :edit
  end

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
      
      render :action => 'wait'
  end
  
  def wait
    render :action => 'wait', :layout => :full
  end
  
  def search_callback
    status = @params['status']
    if (status && status == 'ok' )
      puts "Entrando al if"
      WebView.navigate ( url_for :action => :show_page, :query => @params['search_params'] )
    end
    ""
  end
  
  def show_page
    puts "Peso: #{@params['weight_greater_than']}"
    @freights = Freight.find(:all, :conditions => ["origin LIKE ? AND destination LIKE ? AND CAST(weight AS INT) >= ?", 
                                                    "'%#{@params['origin_name_like']}%'", "'%#{@params['destination_name_like']}%'", @params['weight_greater_than'].to_i ], 
                                  :select => ['origin', 'destination', 'weight', 'category'])
    render :action => :show_page
  end

  def update
    @freight = Freight.find(@params['id'])
    @freight.update_attributes(@params['freight'])
    redirect :action => :index
  end

  def delete
    @freight = Freight.find(@params['id'])
    @freight.destroy
    redirect :action => :index
  end
end
