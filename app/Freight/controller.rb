require 'rho/rhocontroller'

class FreightController < Rho::RhoController

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
    @freight = Freight.new(@params['freight'])
    @freight.save
    redirect :action => :index
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
