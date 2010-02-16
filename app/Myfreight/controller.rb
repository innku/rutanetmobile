require 'rho/rhocontroller'

class MyfreightController < Rho::RhoController

  #GET /Myfreight
  def index
    SyncEngine.dosync
    @myfreights = Myfreight.find(:all)
    render
  end

  # GET /Myfreight/{1}
  def show
    @myfreight = Myfreight.find(@params['id'])
    render :action => :show
  end

  # GET /Myfreight/new
  def new
    @myfreight = Myfreight.new
    render :action => :new
  end

  # GET /Myfreight/{1}/edit
  def edit
    @myfreight = Myfreight.find(@params['id'])
    render :action => :edit
  end

  # POST /Myfreight/create
  def create
    @myfreight = Myfreight.new(@params['myfreight'])
    @myfreight.save
    redirect :action => :index
  end

  # POST /Myfreight/{1}/update
  def update
    @myfreight = Myfreight.find(@params['id'])
    @myfreight.update_attributes(@params['myfreight'])
    redirect :action => :index
  end

  # POST /Myfreight/{1}/delete
  def delete
    @myfreight = Myfreight.find(@params['id'])
    @myfreight.destroy
    redirect :action => :index
  end
end
